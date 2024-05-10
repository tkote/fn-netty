# Functions without FDK 

## How to create a Fn (OCI Functions) function without FDK - Python/FastAPI Edition

I introduced "[implement a Fn function with netty and reactor-netty](https://github.com/tkote/fn-netty)" as a method to create a function without FDK (Function Development Kit) in the past.
And now the Python/FastAPI edition is here. I believe many people would like to develop functions using their familiar framework.

## How it works

There are two parts: 
1. main.py - It implements the logic of Functions
2. fastapi.py - It invokes uvicorn as an ASGI (Asynchronous Server Gateway Interface) implementation, and takes care of sockets in the manner of Fn/OCI Functions. 
  It does not need to be modified at all for any applications.  

Here is a skelton of main.py;

```python
@app.post('/call')
async def post_call():.
    """ acttual processing logic here """
````

See [a netty edition](https://github.com/tkote/fn-netty) if you'd like to know in detail about how Fn/Functions uses the Unix Domain Socket to communicate with a appliction in a container.

## Build & Run

* Run locally

  ```bash
  ## preparation
  $ pip install fastapi
  $ pip install uvicorn

  # run server
  $ python fn-fastapi.py
  
  # call function
  $ curl --unix-socket /tmp/fnlsnr.sock -X POST -d 'Hello World!
  ```

* Create & run Docker image.
  
  ```bash
  # build
  $ docker build -t fn-fastapi:0.0.1 .
  
  # run server
  $ docker run --rm -it --name fn-fastapi -v /tmp:/tmp fn-fastapi:0.0.1

  # call function
  $ curl --unix-socket /tmp/fnlsnr.sock -X POST -d 'Hello World!
  ```

* Run on a local Fn Server
  
  Assuming you already have the Fn CLI installed
  
  ```bash
  # start Fn server
  $ fn start

  # setup Fn CLI
  $ fn use context default
  
  # create app
  $ fn create app funcapp

  # deploy function
  $ fn deploy -app funcapp --local --no-bump -v

  # call function
  $ echo -n 'Hello World!
  ```

* Deploy & Execute in OCI Functions

  Assuming your application funcapp has been created in OCI Functions

  ```bash
  # setup Fn CLI
  $ fn use context XXXXXXXX

  # deploy function
  $ fn deploy -app funcapp --no-bump -v

  # call functions
  $ echo -n 'Hello World!
  ```

## Demo

```
# call OCI Functions
$ echo -n 'Hello World!
[REQUEST HEADERS]
host: localhost
user-agent: go-http-client/1.1
transfer-encoding: chunked
accept-encoding: gzip
content-type: application/json
date: Sun, 09 May 2021 08:37:51 GMT
fn-call-id: 01F584D3CM1BT0010ZJ005T5M1
fn-deadline: 2021-05-09T08:43:16Z
oci-subject-id: ocid1.user.oc1...
oci-subject-tenancy-id: ocid1.tenancy.oc1..
oci-subject-type: user
opc-compartment-id: ocid1.compartment.oc1...
opc-request-id: /01F584D3AJ1BT0010ZJ005T5KR/01F584D3AJ1BT0010ZJ005T5KS
x-content-sha256: f4OxZX/x/FO5LcGBSKHWXfwtSx+j1ncoSt3SABJtkGk=

[REQUEST BODY].
Hello World!
```

Here are logs of OCI Functions - from startup to first request processing;

````
ENV FN_LISTENER: unix:/tmp/iofs/lsnr.sock
actual: /tmp/iofs/lsnr.sock
phony: /tmp/iofs/ldyDK2Jn_lsnr.sock
INFO: Started server process [6]
INFO: Waiting for application startup.
INFO: Application startup complete.
INFO: Uvicorn running on unix socket /tmp/iofs/ldyDK2Jn_lsnr.sock (Press CTRL+C to quit)
Ready to receive calls via /tmp/iofs/lsnr.sock -> ldyDK2Jn_lsnr.sock
INFO: - \"POST /call HTTP/1.1\" 200 OK
```
