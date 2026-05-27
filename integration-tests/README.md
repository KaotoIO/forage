# Integration tests

Integration tests leverage [Citrus Test Framework](https://github.com/citrusframework/citrus)
CLI Forage command is triggered via [Custom integration action](https://github.com/citrusframework/citrus/pull/1428). 

## How to create integration tests

Suggested approach is to described in this page. 
General principles could be found in Citrus test framework [guide](https://citrusframework.org/citrus/reference/4.8.2/html/index.html).
Several simplifications and helper methods are introduced by `io.kaoto.forage:integration-tests-common` module (part of forage project).

Each integration test needs to comply with the following mandatory requirements:

* Each test class has to implement `package io.kaoto.forage.integration.tests;
.ForageIntegrationTest`, which does not require addition of any new method.
* Each test class has to be annotated by 
  ```
  @CitrusSupport
  @ExtendWith(IntegrationTestSetupExtension.class)
  ```

Integration test typically needs to start a Camel application via forage (by providing a forage properties file and camel yaml routes).

```java
runner.when(forageRun("route", "forage-datasource-factory.properties", "route.camel.yaml")
  (1)                   (2)                    (3)                              (4)
```
1. Runner - instance of `ForageTestCaseRunner` - provides access to Citrus behaviour driven development executor. 
More information abou **Given-when-then** structure is available in the [documentation](https://citrusframework.org/citrus/reference/4.8.2/html/index.html#runtimes-cucumber).
2. Process name - an identifier to the camel process which is started in the background. 
Identifier is required everytime test has to interact with running Camel application.
3. Name of the forage properties file (Located in the resources in a sub-folder, named after the test class and located in on the same path as the class)
4. Name of the camel route(s) definition (usually yaml) (Located in the resources in a sub-folder, named after the test class and located in on the same path as the class)

There are two different lifecycles involving Camel process in the background

1. **Start Camel Integration for Each Test Method** - Default behavior. No further code is required.

2. **Start Camel Integration Once Per Test Class** - Implement method `runBeforeAll` from the interface `ForageIntegrationTest`

Typical implementation looks similarly to
```java

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // running jbang forage run with required resources and required runtime
        runner.when(forageRun(INTEGRATION_NAME, "forage-datasource-factory.properties", "jdbc-routes.camel.yaml")
                // required if more test are using the same route
                .autoRemove(false));

        return INTEGRATION_NAME;
    }
```

* Do not forget to mark builder with `.autoRemove(false))`.
This flag prevents Citrus to stop Camel process (running in the background) after each test.
* Return name of the process name or make sure to stop the camel process by yourself. 

## DifferentRuntimes

We have 3 test suites (defined via JUnit5 API).
* `io.kaoto.forage.integration.tests.suites.PlainSuite`
* `io.kaoto.forage.integration.tests.suites.QuarkusSuite`
* `io.kaoto.forage.integration.tests.suites.SpringBootSuite`

Every test is executed trice times (for each different runtime).
You do not need to take care of runtimes when writing an integration tests.
(Implementation, taking care of runtimes, is located in `io.kaoto.forage.integration.tests.ForageTestCaseRunner`).

When you run the integration test via IDE or via cmd, all three runtimes are tested by default.
To select a specific runtime, export `INTEGRATION_TEST_RUNTIME` environment variable.

**Supported INTEGRATION_TEST_RUNTIME values:**
- `main` - Plain Camel Main runtime (alias: `camel-main`)
- `quarkus` - Quarkus runtime
- `spring-boot` - Spring Boot runtime

**Note:** If `INTEGRATION_TEST_RUNTIME` is not set, all three runtimes will be tested (default behavior).

**Examples:**

Run tests with only Quarkus runtime:
```bash
export INTEGRATION_TEST_RUNTIME=quarkus
mvn clean verify -f integration-tests/jdbc -Dit.test=JdbcTest
```

Run tests with only Spring Boot runtime:
```bash
export INTEGRATION_TEST_RUNTIME=spring-boot
mvn clean verify -f integration-tests/jdbc -Dit.test=JdbcTest
```

Run tests with only plain Camel Main runtime:
```bash
export INTEGRATION_TEST_RUNTIME=main
mvn clean verify -f integration-tests/jdbc -Dit.test=JdbcTest
```

Run tests with all runtimes (default behavior):
```bash
unset INTEGRATION_TEST_RUNTIME
mvn clean verify -f integration-tests/jdbc -Dit.test=JdbcTest
```

You should see the runtime for each Camel Action in the log, similar to: 
```java
[main] INFO io.kaoto.forage.integration.tests.ForageTestCaseRunner - --------------------------------------------------------------------------------
[main] INFO io.kaoto.forage.integration.tests.ForageTestCaseRunner - ----------------------------------- quarkus ------------------------------------
[main] INFO io.kaoto.forage.integration.tests.ForageTestCaseRunner - --------------------------------------------------------------------------------
```

## Tips and tricks

* You don't see the console (log) output of the Camel process running in the background during the test execution by default.
Please add property `dumpIntegrationOutput(true)` to dump te logs.
Whole example:
  ```java
  runner.when(forageRun("route", "forage-datasource-factory.properties", "route.camel.yaml")
                .dumpIntegrationOutput(true));  
  ```
* In case you'd like to debug the camel route from the background, add following parameter to the runner execution:  
  ```
  .withArg("--jvm-debug", "5005")
  ```
  The background camel process waits and listens on a port 5005 for java remote debug,
* If you need to find the runtime folder of the Camel process (from the background), please look into `target/test-classes/${full-test-class-path}` 

* Be aware that test for runtimes (`quarkus` or `spring-boot`) runs in a sub-folder with exported application.
This fact affects all relative paths used in the routes - they fail by default (because none of paths exist).
For such case please manually replace relative paths with absolute paths in the `@BeforeAll` method.
Follow `io.kaoto.forage.jdbc.JdbcTest` as an example.

* Please use tests from `io.kaoto.forage:integration-tests-jdbc`  as examples for further details.
