use super::guardian_lambda as gl;

extern crate rusoto_core;
extern crate rusoto_ssm;
extern crate rusoto_credential;

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

fn env_var(key: String, default: String) -> String {
    env::var(key).unwrap_or(default)
}

fn fetch_config(credentials: ChainProvider) -> Result<HashMap<String, String>, String> {
    let mut config = HashMap::new();

    let app = env_var("App".to_string(), "db-cleaner".to_string());
    let stack = env_var("Stack".to_string(), "mobile".to_string());
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

    match ssm_client.get_parameters_by_path(req).sync() {
        Ok(result) => {
            match result.parameters {
                Some(parameters) => {
                    for parameter in parameters {
                        if parameter.name.is_some() && parameter.value.is_some() {
                            let prefixLength = path.len() + 1; // +1 for the extra slash
                            let name = parameter.name.unwrap()[prefixLength..].to_string();
                            config.insert(name, parameter.value.unwrap());
                        }
                    }
                },
                None => error!("No parameter found"),
            };
        }
        Err(error) => {
            error!("Error while fetching parameter: {}", error);
            return Err("Unable to fetch configuration".to_string())
        },
    }

    Ok(config)
}

pub fn lambda<A: gl::AbstractLambdaContext>(e: gl::LambdaInput, context: A) -> Result<gl::LambdaOutput, super::HandlerError> {
    let mut profileProvider: ProfileProvider = ProfileProvider::new().unwrap(); profileProvider.set_profile("mobile");
    let credentials = ChainProvider::with_profile_provider(profileProvider);
    let config = match fetch_config(credentials) {
        Ok(config) => {
            info!("Config loaded from SSM: {:?}", config);
            config
        },
        Err(e) => {
            error!("{}", e);
            return context.new_error(&e)
        }
    };

    info!("Some message here");
    Ok(gl::LambdaOutput {})
}