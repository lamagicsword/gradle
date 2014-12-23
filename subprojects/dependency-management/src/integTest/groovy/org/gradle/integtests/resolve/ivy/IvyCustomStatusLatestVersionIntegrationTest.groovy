/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Ignore
import spock.lang.Issue

class IvyCustomStatusLatestVersionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "latest.xyz selects highest version with given or higher status"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'org.test:projectA:latest.$status'
    components {
        all { ComponentMetadataDetails details ->
            details.statusScheme = ["bronze", "silver", "gold", "platin"]
        }
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('bronze').publish()
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('silver').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('gold').publish()
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('platin').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${version}.jar")

        where:
        status   | version
        "bronze" | "1.3"
        "silver" | "1.3"
        "gold"   | "1.2"
        "platin" | "1.0"
    }

    def "uses status provided by component metadata rule for latest.xyz"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'org.test:projectA:latest.release'
    components {
        all { ComponentMetadataDetails details ->
            if (details.id.version == project.properties['releaseVersion']) {
                details.status = 'release'
            }
        }
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        ivyHttpRepo.directoryList('org.test', 'projectA').allowGet()
        ivyHttpRepo.module('org.test', 'projectA', '1.0').withStatus("release").publish().allowAll()
        ivyHttpRepo.module('org.test', 'projectA', '1.1').withStatus("integration").publish().allowAll()

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.0.jar")

        when:
        executer.withArgument("-PreleaseVersion=1.1")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.1.jar")
    }

    @Ignore
    @Issue("https://issues.gradle.org/browse/GRADLE-3216")
    def "uses changing provided by component metadata rule for latest.xyz"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'org.test:projectA:latest.release'
    components {
        all { ComponentMetadataDetails details ->
            if(details.status == 'snapshot') {
                details.changing = true
            }

            details.statusScheme = ['snapshot', 'release']
        }
    }
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor 0, 'seconds'
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        ivyHttpRepo.directoryList('org.test', 'projectA').allowGet()
        ivyHttpRepo.module('org.test', 'projectA', '1.1').withStatus("snapshot").publish().allowAll()
        ivyHttpRepo.module('org.test', 'projectA', '1.2').withStatus("release").publish().allowAll()
        ivyHttpRepo.module('org.test', 'projectA', '1.3').withStatus("snapshot").publish().allowAll()


        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.2.jar")

        when:
        run 'retrieve'

        then:
        executer.withArgument("--refresh-dependencies")
        file('libs').assertHasDescendants("projectA-1.2.jar")

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.2.jar")
    }

    @Ignore
    @Issue("https://issues.gradle.org/browse/GRADLE-3216")
    def "handles changing module with latest.release"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile group: "org.test", name: "projectA", version: "latest.release", changing: true
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor 0, 'seconds'
    }
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        ivyHttpRepo.directoryList('org.test', 'projectA').allowGet()
        ivyHttpRepo.module('org.test', 'projectA', '1.2').withStatus("release").publish().allowAll()

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.2.jar")

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.2.jar")
    }
}
