use super::guardian_lambda as gl;

extern crate rusoto_core;
extern crate rusoto_ssm;

use rusoto_core::Region;
use rusoto_core::DefaultCredentialsProvider;
use rusoto_ssm::SsmClient;
use rusoto_ssm::GetParameterRequest;
use crate::cleaner::rusoto_ssm::Ssm;

pub fn lambda<A: gl::AbstractLambdaContext>(e: gl::LambdaInput, context: A) -> Result<gl::LambdaOutput, super::HandlerError> {
    let credentials = DefaultCredentialsProvider::new().unwrap();
    let ssm_client = SsmClient::new(Region::EuWest1);

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