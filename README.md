# Functions without FDK

## Fn (OCI Functions) の Function を FDK を使わずに作成する方法

* HTTP Server over Unix Domain Socket を実装する
* POST /call で呼び出される、これをハンドルする
* bind する ファイルのパスは環境変数 FN_LISTENER で渡される (/tmp/iofs/lsnr.sock)
  - 直接 bind せずに 指定されたパスと同一ディレクトリの別のファイル名 (例: YvzDu6m9_lsnr.sock)で bind する
  - このファイルに rw-rw-rw- のアクセス権限を設定する
  - これに相対パスのシンボリックリンクを張る
  - 結果、実行時にはこんなファイル構成となっている
    ```
    lrwxrwxrwx. 1 root root 17 Mar  3 17:45 /tmp/iofs/lsnr.sock -> YvzDu6m9_fnlsnr.sock
    srw-rw-rw-. 1 root root  0 Mar  3 17:45 /tmp/iofs/YvzDu6m9_lsnr.sock
    ```

## 実装

つまり Unix Domain Socket に対応した HTTP Server フレームワークを使うのが手っ取り早い  
ということで、今回は netty と reactor-netty で実装してみた  

* netty 版  
  [Netty HTTP Example の snoop](https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example/http/snoop) をベースにアレンジした

* reactor-netty 版  
  snoopと同じ出力になるように実装

## netty 使用時の実装 tips
* netty は Unix Domain Socket を扱うための native library (.so) が必要で、デフォルトでは実行時にダイナミックにファイルを配置するようで、Dockerコンテナ内で動作させるとうまく動作しない模様 - imageのビルド時に特定のディレクトリに native library を配置して、起動オプションで `-Djava.library.path=` を指定することによってこの問題を回避した、本来はちゃんと原因追及するべき...
* OCI Functions のメモリーの設定に気をつける (128 では何も吐かずに勝手に落ちる)

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
  $ fn deploy -app funcapp --build-arg mainClass=$mainClass --local --no-bump -v

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
  HEADER: Date = Sat, 13 Mar 2021 03:38:15 GMT
  HEADER: Fn-Call-Id = 01F0MTKJ5B1BT0G60ZJ001JFKX
  HEADER: Fn-Deadline = 2021-03-13T03:43:48Z
  HEADER: Oci-Subject-Id = ocid1.user.oc1..
  HEADER: Oci-Subject-Tenancy-Id = ocid1.tenancy.oc1..
  HEADER: Oci-Subject-Type = user
  HEADER: Opc-Compartment-Id = ocid1.compartment.oc1..
  HEADER: Opc-Request-Id = /01F0MTKJ331BT0G60ZJ001JFKV/01F0MTKJ331BT0G60ZJ001JFKW
  HEADER: X-Content-Sha256 = A7ogTlDRJuRnTABeBNguhMITZngK8fQ71Uo3gWtqs0A=
  
  CONTENT: Hello World!
  END OF CONTENT
  ```
  
  OCI Functions `Content-Type: application/json` になってる...
