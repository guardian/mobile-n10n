
use super::guardian_lambda as gl;

pub fn logic<A: gl::AbstractLambdaContext>(e: gl::LambdaInput, a: A) -> Result<gl::LambdaOutput, super::HandlerError> {
    if 1 != 1 {
        error!("Empty first name in request {}", a.request_id());
        return a.new_error("Empty first name");
    }
    info!("Some message here");
    Ok(gl::LambdaOutput {})
}