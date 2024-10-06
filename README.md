## run

```
clj -M -m shell2http-clj.main
```

## build jar
```
clj -T:build uberjar
```

## build native

```
native-image --report-unsupported-elements-at-runtime --no-server --no-fallback --initialize-at-build-time --install-exit-handlers -jar ./target/shell2http_clj-0.0.1.jar -H:Name=./target/shell2http_clj-0.0.1
```

## test

```
target/shell2http_clj-0.0.1 --echo /test 'bash -c "for x in {1..5}; do sleep 1; echo line $x; done"'
```

```
curl.exe -v localhost:8080/test
```

```
clj -M -m shell2http-clj.main --echo --add-exit --form /py "python slow_log.py" /env "env" /log "tail -f /var/log
```
