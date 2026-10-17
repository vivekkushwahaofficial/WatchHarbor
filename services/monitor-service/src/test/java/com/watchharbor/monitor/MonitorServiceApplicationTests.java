
package com.watchharbor.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MonitorServiceApplicationTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner();

    @Test
    void testContextRunnerStartsSuccessfully() {
        contextRunner.run(context ->
                assertThat(context).hasNotFailed()
        );
    }
}
