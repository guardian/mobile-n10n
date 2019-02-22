use lambda::error::HandlerError;

pub trait AbstractLambdaContext {
    fn request_id(&self) -> String;
    fn new_error(&self, msg: &str) -> Result<LambdaOutput, HandlerError>;
    fn is_local(&self) -> bool;
}

pub struct AWSContext {
    pub c: lambda::Context
}

impl AbstractLambdaContext for AWSContext {
    fn request_id(&self) -> String {
        self.c.aws_request_id.clone()
    }
    fn new_error(&self, msg: &str) -> Result<LambdaOutput, HandlerError> {
        Err(self.c.new_error(msg))
    }

    fn is_local(&self) -> bool { false }
}

pub struct LocalContext {}

impl AbstractLambdaContext for LocalContext {
    fn request_id(&self) -> String {
        "abc".to_string()
    }
    fn new_error(&self, msg: &str) -> Result<LambdaOutput, HandlerError> {
        error!("{}", msg);
        Ok(LambdaOutput{})
    }

    fn is_local(&self) -> bool { true }
}

#[derive(Deserialize, Clone)]
pub struct LambdaInput {
}

#[derive(Serialize, Clone)]
pub struct LambdaOutput {
}

