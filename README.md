## build jar
```
clj -T:build uberjar
```

## build native

```
native-image --report-unsupported-elements-at-runtime --no-server --no-fallback --initialize-at-build-time -jar ./target/shell2http_clj-0.0.1.jar -H:Name=./target/shell2http_clj-0.0.1`
```
