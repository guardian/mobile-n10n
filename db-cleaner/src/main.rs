#[macro_use]
extern crate lambda_runtime as lambda;
#[macro_use]
extern crate serde_derive;
#[macro_use]
extern crate log;
extern crate simple_logger;

use std::error::Error;
use std::env;

mod guardian_lambda;
mod cleaner;

use lambda::error::HandlerError;

fn main() -> Result<(), Box<dyn Error>> {

    simple_logger::init_with_level(log::Level::Info);

    if env::var("AWS_REGION").is_err() {
        info!("Using local context");
        let a = guardian_lambda::LocalContext {};
        let e = guardian_lambda::LambdaInput {};
        match cleaner::lambda(e, a) {
            Ok(result) => info!("Success"),
            Err(e) => error!("{}", e),
        };
        return Ok(());
    }

    lambda!(handler);

    Ok(())
}

fn handler(e: guardian_lambda::LambdaInput, c: lambda::Context) -> Result<guardian_lambda::LambdaOutput, HandlerError> {
    let a = guardian_lambda::AWSContext { c };
    cleaner::lambda(e, a)
}