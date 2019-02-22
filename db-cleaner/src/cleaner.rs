use super::guardian_lambda as gl;

extern crate rusoto_core;
extern crate rusoto_ssm;
extern crate rusoto_credential;
extern crate postgres;
extern crate url;

use url::Url;
use rusoto_core::Region;
use rusoto_core::request::HttpClient;
use rusoto_ssm::SsmClient;
use rusoto_ssm::GetParameterRequest;
use crate::cleaner::rusoto_ssm::Ssm;
use rusoto_credential::ChainProvider;
use rusoto_credential::ProfileProvider;
use std::collections::HashMap;
use std::env;
use self::rusoto_ssm::GetParametersByPathRequest;
use postgres::{Connection, TlsMode};

struct ConnectionParameters {
    jdbc_url: String,
    user: String,
    password: String,
}

fn get_credentials() -> Result<ChainProvider, String> {
    ProfileProvider::new().map(| mut profile_provider| {
        profile_provider.set_profile("mobile");
        ChainProvider::with_profile_provider(profile_provider)
    }).map_err(|e| e.message)
}

fn env_var(key: String, default: String) -> String {
    env::var(key).unwrap_or(default)
}

fn fetch_config(credentials: ChainProvider) -> Result<HashMap<String, String>, String> {
    let mut config = HashMap::new();

    let stage = env_var("Stage".to_string(), "DEV".to_string());

    let ssm_client = SsmClient::new_with(
        HttpClient::new().expect("failed to create request dispatcher"),
        credentials,
        Region::EuWest1
    );

    let path: String = "/notifications/".to_owned() + &stage + "/workers";

    let req = GetParametersByPathRequest {
        max_results: None,
        next_token: None,
        parameter_filters: None,
        path: path.to_string(),
        recursive: Some(true),
        with_decryption: Some(true),

    };

    ssm_client.get_parameters_by_path(req).sync()
        .map_err(|error| {
            error!("Error while fetching parameter: {}", error);
            "Unable to fetch configuration".to_string()
        })
        .and_then(|result| {
            result.parameters.ok_or("No Parameter Found".to_string())
        })
        .map(|parameters| {
            for parameter in parameters {
                if parameter.name.is_some() && parameter.value.is_some() {
                    let prefixLength = path.len() + 1; // +1 for the extra slash
                    let name = parameter.name.unwrap()[prefixLength..].to_string();
                    config.insert(name, parameter.value.unwrap());
                }
            }
            config
        })
        .and_then(|config| {
            if config.is_empty() {
                error!("No configuration loaded");
                Err("Unable to fetch configuration".to_string())
            } else { Ok(config) }
        })
}

fn config_to_connection_parameter(config: HashMap<String, String>) -> Result<ConnectionParameters, String> {
    config.get("cleaner.registration.db.url").and_then(|jdbc_url| {
        Some(ConnectionParameters {
            jdbc_url: jdbc_url.to_owned(),
            user: config.get("cleaner.registration.db.user")?.to_owned(),
            password: config.get("cleaner.registration.db.password")?.to_owned(),
        })
    }).ok_or("Unable to read the url, user or password from the configuration".to_string())
}

fn create_connection_url(connection_parameters: ConnectionParameters, local: bool) -> Result<String, String> {
    let url = connection_parameters.jdbc_url.replace("jdbc:", "");
    match Url::parse(&url) {
        Ok(mut url) => {
            url.set_username(&connection_parameters.user);
            url.set_password(Some(&connection_parameters.password));
            url.set_query(None);

            if local { url.set_host(Some("localhost")); };

            Ok(url.as_str().to_string())
        }
        Err(e) => Err(e.to_string())
    }

}

pub fn lambda<A: gl::AbstractLambdaContext>(e: gl::LambdaInput, context: A) -> Result<gl::LambdaOutput, super::HandlerError> {
    let result = get_credentials()
        .and_then(fetch_config)
        .and_then(config_to_connection_parameter)
        .and_then(|params| create_connection_url(params, context.is_local()))
        .and_then(|connection_url| Connection::connect(connection_url, TlsMode::None).map_err(|e| e.to_string()))
        .and_then(|connection| connection.execute("SELECT 1;", &[]).map_err(|e| e.to_string()));

    match result {
        Ok(row_count) => {
            info!("Deleted {} rows", row_count);
            Ok(gl::LambdaOutput {})
        }
        Err(error_string) => context.new_error(&error_string)
    }
}