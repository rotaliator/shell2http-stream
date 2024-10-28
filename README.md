## run using clojure

```
clj -M -m shell2http-stream.main
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


## Manual testing

```
clj -M -m shell2http-stream.main --echo /test 'bash -c "for x in {1..5}; do sleep 1; echo hello!; done"'
```

```
curl -v localhost:8080/test
```

### log file watch
```
clj -M -m shell2http-stream.main --echo --add-exit /log "tail -f /var/log/example.log"
```

```
curl -v localhost:8080/log
```
