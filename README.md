# Functions without FDK

## Fn (OCI Functions) の Function を FDK を使わずに作成する方法

* HTTP Server over Unix Domain Socket を実装する
* POST /call で呼び出される、これをハンドルする
* bind する ファイルのパスは環境変数 FN_LISTENER で渡される  
  例: `/tmp/iofs/lsnr.sock`
  - 直接 bind せずに 指定されたパスと同一ディレクトリの別のファイル名で bind する  
    例: `/tmp/iofs/YvzDu6m9_lsnr.sock`
  - このファイルに rw-rw-rw- のアクセス権限を設定する
  - これに相対パスのシンボリックリンクを張る
  - 結果、実行時にはこんなファイル構成となっている
    ```
    $ ls -l /tmp/iofs
    lrwxrwxrwx. 1 root root 17 Mar  3 17:45 lsnr.sock -> YvzDu6m9_lsnr.sock
    srw-rw-rw-. 1 root root  0 Mar  3 17:45 YvzDu6m9_lsnr.sock
    ```

## How to create an Fn (OCI Functions) Function without FDK

+ Implement HTTP Server over Unix Domain Socket
+ Handle POST /call
+ The file name to be bound is passed via the environment variable FN_LISTENER  
  example: /tmp/iofs/lsnr.sock
  - Bind socket to another file which is different from FN_LISTENER located in the same directory  
    example: /tmp/iofs/YvzDu6m9_lsnr.sock
  - Set rw-rw-rw- permission to this file
  - Create a symbolic link named FN_LISTENER which is linked to this file with a relative path
  - As a result, the placement of files is like this
    ```
    $ ls -l /tmp/iofs
    lrwxrwxrwx. 1 root root 17 Mar  3 17:45 lsnr.sock -> YvzDu6m9_lsnr.sock
    srw-rw-rw-. 1 root root  0 Mar  3 17:45 YvzDu6m9_lsnr.sock
    ```


## 実装

つまり Unix Domain Socket に対応した HTTP Server フレームワークを使うのが手っ取り早い  
ということで、今回は netty と reactor-netty で実装してみた  

* netty 版  
  [Netty HTTP Example の snoop](https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example/http/snoop) をベースにアレンジした

* reactor-netty 版  
  snoopと同じ出力になるように実装

## netty 使用時の実装 tips
* netty は Unix Domain Socket を扱うための native library (.so) が必要で、デフォルトでは実行時にダイナミックにファイルを配置するようで、Dockerコンテナ内で動作させるとうまく動作しない模様  
  → imageのビルド時に特定のディレクトリに native library を配置して、起動オプションで `-Djava.library.path=xxx` を指定することによってこの問題を回避した、本来はちゃんと原因追及するべき...
* OCI Functions のメモリー量の指定に気をつける (128 では小さくて何も吐かずに勝手に落ちた)

## ビルド & 実行

環境変数 mainClass は最初にセットしておく

```bash
# netty 版
$ export mainClass=org.example.netty.FnServer
# reactor-netty 版
$ export mainClass=org.example.reactor.FnServer
```

* ローカルでビルド & 実行

  ```bash
  # build
  $ mvn clean package

  # run server
  $ java -cp target/fn-netty.jar $mainClass
  
  # call function
  $ curl --unix-socket /tmp/fnlsnr.sock -X POST -d 'Hello World!' http:/call
  ```

* Docker imageを作成 & 実行
  
  ```bash
  # build
  $ docker build --build-arg mainClass=$mainClass -t fn-netty:0.0.1 .
  
  # run server
  $ docker run --rm -it --name fn-netty -v /tmp:/tmp fn-netty:0.0.1

  # call function
  $ curl --unix-socket /tmp/fnlsnr.sock -X POST -d 'Hello World!' http:/call
  ```

* ローカル Fn Server で実行
  
  Fn CLIはインストール済みという前提で
  
  ```bash
  # start Fn server
  $ fn start

  # setup Fn CLI
  $ fn use context default
  
  # create app
  $ fn create app funcapp

  # deploy function
  $ fn deploy -app funcapp --build-arg mainClass=$mainClass --local --no-bump -v

  # call function
  $ echo -n 'Hello World!' | fn invoke funcapp fn-netty
  ```

* OCI Functions にデプロイ & 実行

  OCI Functionsで アプリケーション funcapp が作成されている前提で

  ```bash
  # setup Fn CLI
  $ fn use context XXXXXX

  # deploy function
  $ fn deploy -app funcapp --build-arg mainClass=$mainClass --no-bump -v

  # call functions
  $ echo -n 'Hello World!' | fn invoke funcapp fn-netty
  ```

## デモ

* Local 環境

  ```
  $ curl --unix-socket /tmp/fnlsnr.sock -X POST -d 'Hello World!' http:/call
  FN-NETTY (REACTOR) SERVER
  ===================================
  VERSION: HTTP/1.1
  HOSTNAME: http
  REQUEST_URI: /call
  
  HEADER: User-Agent = curl/7.29.0
  HEADER: Host = http
  HEADER: Accept = */*
  HEADER: Content-Length = 12
  HEADER: Content-Type = application/x-www-form-urlencoded
  
  CONTENT: Hello World!
  END OF CONTENT
  ```

* Local Fn Server

  ```
  $ echo -n 'Hello World!' | fn invoke funcapp fn-netty
  FN-NETTY (REACTOR) SERVER
  ===================================
  VERSION: HTTP/1.1
  HOSTNAME: localhost
  REQUEST_URI: /call
  
  HEADER: Host = localhost
  HEADER: User-Agent = Go-http-client/1.1
  HEADER: Transfer-Encoding = chunked
  HEADER: Accept-Encoding = gzip
  HEADER: Content-Type = text/plain
  HEADER: Fn-Call-Id = 01F0MT4Q21NG8G00GZJ0000005
  HEADER: Fn-Deadline = 2021-03-13T03:35:10Z
  
  CONTENT: Hello World!
  END OF CONTENT
  ```

* OCI Functions

  ```
  $ echo -n 'Hello World!' | fn invoke funcapp fn-netty
  FN-NETTY (REACTOR) SERVER
  ===================================
  VERSION: HTTP/1.1
  HOSTNAME: localhost
  REQUEST_URI: /call
  
  HEADER: Host = localhost
  HEADER: User-Agent = Go-http-client/1.1
  HEADER: Transfer-Encoding = chunked
  HEADER: Accept-Encoding = gzip
  HEADER: Content-Type = application/json
  HEADER: Date = Fri, 30 Jul 2021 20:53:55 GMT
  HEADER: Fn-Call-Id = 01FBWK3SVZ1BT0H18ZJ000MVYQ
  HEADER: Fn-Deadline = 2021-07-30T20:58:53Z
  HEADER: Oci-Subject-Compartment-Id = ocid1.tenancy.oc1..xxxxxx
  HEADER: Oci-Subject-Id = ocid1.user.oc1..xxxxxx
  HEADER: Oci-Subject-Tenancy-Id = ocid1.tenancy.oc1..xxxxxx
  HEADER: Oci-Subject-Type = user
  HEADER: Opc-Compartment-Id = ocid1.compartment.oc1..xxxxxx
  HEADER: Opc-Request-Id = /01FBWK3ST90000000000015JDG/01FBWK3ST90000000000015JDH
  HEADER: X-B3-Spanid = 4d52a9209c1ea317
  HEADER: X-B3-Traceid = 4d52a9209c1ea317
  HEADER: X-Content-Sha256 = f4OxZX/x/FO5LcGBSKHWXfwtSx+j1ncoSt3SABJtkGk=
  
  CONTENT: Hello World!
  END OF CONTENT
  ```
  
  OCI Functions `Content-Type: application/json` になってる...


* OCI API Gateway -> OCI Functions

  ```
  $curl -X POST -d "[]" -H "Content-Type: application/json" https://xxxxxx.apigateway.us-ashburn-1.oci.customer-oci.com/fn-netty/

  FN-NETTY (REACTOR) SERVER
  ===================================
  VERSION: HTTP/1.1
  HOSTNAME: localhost
  REQUEST_URI: /call

  HEADER: Host = localhost
  HEADER: User-Agent = lua-resty-http/0.14 (Lua) ngx_lua/10019
  HEADER: Transfer-Encoding = chunked
  HEADER: Content-Type = application/json
  HEADER: Date = Fri, 30 Jul 2021 20:26:32 GMT
  HEADER: Fn-Call-Id = 01FBWHHP481BT0J1GZJ000PM8T
  HEADER: Fn-Deadline = 2021-07-30T20:31:43Z
  HEADER: Fn-Http-H-Accept = */*
  HEADER: Fn-Http-H-Cdn-Loop = fdJfCZhy618AGmi5huTgzQ
  HEADER: Fn-Http-H-Content-Length = 2
  HEADER: Fn-Http-H-Content-Type = application/json
  HEADER: Fn-Http-H-Forwarded = for=129.213.131.125
  HEADER: Fn-Http-H-Host = xxxxxx.apigateway.us-ashburn-1.oci.customer-oci.com
  HEADER: Fn-Http-H-User-Agent = curl/7.29.0
  HEADER: Fn-Http-H-X-Forwarded-For = xxx.xxx.xxx.xxx
  HEADER: Fn-Http-H-X-Real-Ip = xxx.xxx.xxx.xxx
  HEADER: Fn-Http-Method = POST
  HEADER: Fn-Http-Request-Url = /fn-netty/
  HEADER: Fn-Intent = httprequest
  HEADER: Fn-Invoke-Type = sync
  HEADER: Oci-Subject-Compartment-Id = ocid1.compartment.oc1..xxxxxx
  HEADER: Oci-Subject-Id = ocid1.apigateway.oc1.iad.xxxxxx
  HEADER: Oci-Subject-Tenancy-Id = ocid1.tenancy.oc1..xxxxxx
  HEADER: Oci-Subject-Type = resource
  HEADER: Opc-Request-Id = /6997BF9ED7E80B78B3834B7EB7054184/01FBWHHP3X0000000000019C89
  HEADER: X-B3-Spanid = 5642ee33c9d0c82c
  HEADER: X-B3-Traceid = 5642ee33c9d0c82c
  HEADER: X-Content-Sha256 = T1PNoYwrqgwDVLtfmj7L5e0Sq02OEbqHPC8RFhICuUU=
  HEADER: Accept-Encoding = gzip

  CONTENT: []
  END OF CONTENT
  ```
  
  `Fn-Http-xxx` でAPI Gatewayが受けっとったHTTPリクエストの内容が転送されているのが分かる

