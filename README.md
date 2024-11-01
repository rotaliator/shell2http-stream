# shell2http-stream

`shell2http-stream` is a web server that allows you to execute shell commands and stream their output over HTTP. It is heavily inspired by the excellent [shell2http](https://github.com/msoap/shell2http) but enhances it by enabling real-time streaming of command outputs. This feature makes it particularly useful for scenarios such as streaming log files or monitoring long-running commands.

The project is written in `Clojure` and can be compiled to a native binary using GraalVM's native-image.


## Usage

```
Usage: shell2http-stream [options] /path "shell command" /path2 "shell command2"
options:
  --help                 Prints this info and exits
  --no-index     false   Do not generate an index page
  --add-exit     false   Adds an /exit command
  --echo         false   Reprints command output to stdout
  --trigger-only false   Only executes the command without returning output
  --form         false   Populates environment variables from query parameters
  --host         0.0.0.0 The hostname to listen on (default: 0.0.0.0)
  --port         8080    The port to listen on (default: 8080)
```

## Key differences from `shell2http`

* Both standard output and standard error are returned in the response (there are no `-show-errors` or `-include-stderr` options).
* All user-defined endpoints return a status code 200 and stream command output line by line (may change in future releases)
* No support for CGI-mode (yet?)
* No support for SSL (yet?)
* The `--echo` option: When this option is enabled, command output is printed to stdout in addition to being returned in the HTTP response. This is particularly useful when `shell2http-stream` is running in a containerized environment with a log collector like Promtail or Logstash.

## Status

The project is currently in the early stages of development. Please note that breaking changes may occur in future updates.

## Run using clojure

```
clj -M -m shell2http-stream.main --help
```

## build jar
```
clj -T:build uberjar
```

or using Babashka:
```
bb build-jar
```

## build native

```
native-image --report-unsupported-elements-at-runtime --no-server --no-fallback --initialize-at-build-time --install-exit-handlers -jar ./target/shell2http-stream-${VERSION}.jar -H:Name=./target/shell2http-stream
```
or using Babashka:
```
bb build-native
```

## Running tests

```
clj -X:test
```

## Manual testing

```
clj -M -m shell2http-stream.main --echo /test 'bash -c "for x in {1..5}; do sleep 1; echo hello!; done"'
```
```
curl localhost:8080/test
```

### Log file watch
```
clj -M -m shell2http-stream.main --echo --add-exit /log "tail -f /var/log/example.log"
```
```
curl localhost:8080/log
```

## License

[UNLICENSE](UNLICENSE)
