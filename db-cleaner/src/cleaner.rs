use super::guardian_lambda as gl;

extern crate rusoto_core;
extern crate rusoto_ssm;
extern crate rusoto_credential;

use rusoto_core::Region;
use rusoto_core::request::HttpClient;
use rusoto_core::DefaultCredentialsProvider;
use rusoto_ssm::SsmClient;
use rusoto_ssm::GetParameterRequest;
use crate::cleaner::rusoto_ssm::Ssm;
use rusoto_credential::ChainProvider;
use rusoto_credential::ProfileProvider;

pub fn lambda<A: gl::AbstractLambdaContext>(e: gl::LambdaInput, context: A) -> Result<gl::LambdaOutput, super::HandlerError> {
    let mut profileProvider: ProfileProvider = ProfileProvider::new().unwrap(); profileProvider.set_profile("mobile");
    let credentials = ChainProvider::with_profile_provider(profileProvider);
    let ssm_client = SsmClient::new_with(
        HttpClient::new().expect("failed to create request dispatcher"),
        credentials,
        Region::EuWest1
    );

    let req = GetParameterRequest {
        name: "/notifications/CODE/workers/cleaner.registration.db.url".to_string(),
        with_decryption: None,
    };
    match ssm_client.get_parameter(req).sync() {
        Ok(result) => {
            match result.parameter.and_then(|p| p.value) {
                Some(value) => info!("Value from SSM: {}", value),
                None => error!("No parameter found"),
            };
        }
        Err(error) => {
            error!("Error while fetching parameter: {}", error);
            return context.new_error("Empty first name");
        },
    }


    if 1 != 1 {
        error!("Empty first name in request {}", context.request_id());
        return context.new_error("Empty first name");
    }
    info!("Some message here");
    Ok(gl::LambdaOutput {})
}