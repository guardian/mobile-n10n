extern crate rusoto_core;
extern crate rusoto_ssm;
extern crate rusoto_credential;
extern crate postgres;
extern crate url;

use url::Url;
use rusoto_core::Region;
use rusoto_core::request::HttpClient;
use rusoto_ssm::SsmClient;
use crate::cleaner::rusoto_ssm::Ssm;
use rusoto_credential::ChainProvider;
use rusoto_credential::ProfileProvider;
use std::collections::HashMap;
use std::env;
use self::rusoto_ssm::GetParametersByPathRequest;
use postgres::{Connection, TlsMode};
use self::postgres::Error as PgError;

#[derive(PartialEq, Debug)]
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

    fn recursive_fetch(
        ssm_client: SsmClient,
        path: String,
        mut current_config: HashMap<String, String>,
        next_token: Option<String>
    ) -> Result<HashMap<String, String>, String> {
        let req = GetParametersByPathRequest {
            max_results: None,
            next_token: next_token,
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
                for parameter in result.parameters.unwrap_or(Vec::new()) {
                    if parameter.name.is_some() && parameter.value.is_some() {
                        let prefix_length = path.len() + 1; // +1 for the extra slash
                        let name = parameter.name.unwrap()[prefix_length..].to_string();
                        current_config.insert(name, parameter.value.unwrap());
                    }
                }

                match result.next_token {
                    Some(next_token) => recursive_fetch(ssm_client, path, current_config, Some(next_token)),
                    None => Ok(current_config)
                }
            })
    };

    let stage = env_var("Stage".to_string(), "DEV".to_string());

    let ssm_client = SsmClient::new_with(
        HttpClient::new().expect("failed to create request dispatcher"),
        credentials,
        Region::EuWest1
    );

    let path: String = "/notifications/".to_owned() + &stage + "/workers/cleaner";

    let config = HashMap::new();

    recursive_fetch(ssm_client, path, config, None)
        .and_then(|current_config| {
            if current_config.is_empty() {
                error!("No configuration loaded");
                Err("Unable to fetch configuration".to_string())
            } else { Ok(current_config) }
        })
}

fn config_to_connection_parameter(config: HashMap<String, String>) -> Result<ConnectionParameters, String> {
    config.get("registration.db.url").and_then(|jdbc_url| {
        Some(ConnectionParameters {
            jdbc_url: jdbc_url.to_owned(),
            user: config.get("registration.db.user")?.to_owned(),
            password: config.get("registration.db.password")?.to_owned(),
        })
    }).ok_or("Unable to read the url, user or password from the configuration".to_string())
}

fn create_connection_url(connection_parameters: ConnectionParameters, local: bool) -> Result<String, String> {
    let url = connection_parameters.jdbc_url.replace("jdbc:", "");
    match Url::parse(&url) {
        Ok(mut url) => {
            url.set_username(&connection_parameters.user).map_err(|_| "Unable to set the user name of the database URL")?;
            url.set_password(Some(&connection_parameters.password)).map_err(|_| "Unable to set the password of the database URL")?;
            url.set_query(None);

            if local {
                url.set_host(Some("localhost")).map_err(|e| e.to_string())?;
            };

            Ok(url.as_str().to_string())
        }
        Err(e) => Err(e.to_string())
    }

}

fn with_connection<A>(connection_url: String, function: &Fn(&Connection) -> Result<A, PgError>) -> Result<A, PgError> {
    let connection = Connection::connect(connection_url, TlsMode::None)?;
    let result = function(&connection);
    connection.finish()?;
    result
}

fn execute_delete(connection: &Connection) -> Result<u64, PgError> {
    let request = "delete from registrations.registrations where lastmodified <= now() - interval '120 days';";
    connection.execute(request, &[])
}

pub fn delete_outdated_rows(local_context: bool) -> Result<u64, String> {
    get_credentials()
        .and_then(fetch_config)
        .and_then(config_to_connection_parameter)
        .and_then(|params| create_connection_url(params, local_context))
        .and_then(|connection_url| with_connection(connection_url, &execute_delete).map_err(|e| e.to_string()))
}

#[cfg(test)]
mod test_create_connection_url {
    use super::*;

    #[test]
    fn test_create_connection_url() {
        let params = ConnectionParameters {
            jdbc_url: "jdbc:postgres://somedomain.com/somedatabase?paramThatShouldDisapear".to_string(),
            user: "user".to_string(),
            password: "password".to_string(),
        };
        let computed_url = create_connection_url(params, false);
        assert_eq!(computed_url, Ok("postgres://user:password@somedomain.com/somedatabase".to_string()));
    }

    #[test]
    fn test_create_connection_url_local() {
        let params = ConnectionParameters {
            jdbc_url: "jdbc:postgres://somedomain.com/somedatabase?paramThatShouldDisapear".to_string(),
            user: "user".to_string(),
            password: "password".to_string(),
        };
        let computed_url = create_connection_url(params, true);
        assert_eq!(computed_url, Ok("postgres://user:password@localhost/somedatabase".to_string()));
    }

    #[test]
    fn test_create_connection_url_invalid_url() {
        let params = ConnectionParameters {
            jdbc_url: "jdbc:postgres://somedomain\\.com/somedatabase?paramThatShouldDisapear".to_string(),
            user: "user".to_string(),
            password: "password".to_string(),
        };
        let computed_url = create_connection_url(params, true);
        assert_eq!(computed_url.is_err(), true);
    }
}

#[cfg(test)]
mod test_config_to_connection_parameter {
    use super::*;

    #[test]
    fn test_config_to_connection_parameter() {
        let mut config = HashMap::new();
        config.insert("registration.db.url".to_owned(), "someUrl".to_owned());
        config.insert("registration.db.user".to_owned(), "user".to_owned());
        config.insert("registration.db.password".to_owned(), "password".to_owned());

        let params = config_to_connection_parameter(config);
        let expected = ConnectionParameters {
            jdbc_url: "someUrl".to_string(),
            user: "user".to_string(),
            password: "password".to_string(),
        };
        assert_eq!(params, Ok(expected));
    }
}