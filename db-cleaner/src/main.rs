#[macro_use]
extern crate lambda_runtime as lambda;
#[macro_use]
extern crate serde_derive;
#[macro_use]
extern crate log;
extern crate simple_logger;

use std::error::Error;
use std::env;

mod cleaner;

use lambda::error::HandlerError;

#[derive(Deserialize, Clone)]
pub struct LambdaInput {
}

#[derive(Serialize, Clone)]
pub struct LambdaOutput {
}


fn main() -> Result<(), Box<dyn Error>> {

    simple_logger::init_with_level(log::Level::Info);

    if env::var("AWS_REGION").is_err() {
        info!("Using local context");
        match cleaner::delete_outdated_rows(true) {
            Ok(row_count) => info!("Deleted {} rows", row_count),
            Err(e) => error!("{}", e),
        };
        return Ok(());
    }

    lambda!(handler);

    Ok(())
}

fn handler(_e: LambdaInput, context: lambda::Context) -> Result<LambdaOutput, HandlerError> {
    match cleaner::delete_outdated_rows(false) {
        Ok(row_count) => {
            info!("Deleted {} rows", row_count);
            Ok(LambdaOutput {})
        }
        Err(error_string) => Err(context.new_error(&error_string))
    }
}