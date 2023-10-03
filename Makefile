OPENJDK_VERSION=17.0.1
OPENJDK_FILE="https://artifactory.oci.oraclecorp.com/build-service-generic-local/JDK/17/openjdk-${OPENJDK_VERSION}_linux-x64_bin.tar.gz"
OPENJDK_HOME="/usr/lib/jvm/jdk-${OPENJDK_VERSION}"

OPENJDK_VERSION_ARM=17.0.2
OPENJDK_FILE_ARM="https://artifactory.oci.oraclecorp.com/build-service-generic-local/JDK/17/openjdk-${OPENJDK_VERSION_ARM}_linux-aarch64_bin.tar.gz"
OPENJDK_HOME_ARM="/usr/lib/jvm/jdk-${OPENJDK_VERSION_ARM}"

install-java-17:
	mkdir "${OPENJDK_HOME}"
	curl -fsSL -o /tmp/openjdk.tar "${OPENJDK_FILE}"
	tar -C "${OPENJDK_HOME}" --strip-components 1 -xf /tmp/openjdk.tar
	update-alternatives --install /usr/bin/java java "${OPENJDK_HOME}/bin/java" 1000
	update-alternatives --install /usr/bin/javac javac "${OPENJDK_HOME}/bin/javac" 1000
	update-alternatives --set java "${OPENJDK_HOME}/bin/java"
	java -version

install-java-17-arm:
	mkdir "${OPENJDK_HOME_ARM}"
	curl -fsSL -o /tmp/openjdk.tar "${OPENJDK_FILE_ARM}"
	tar -C "${OPENJDK_HOME_ARM}" --strip-components 1 -xf /tmp/openjdk.tar
	update-alternatives --install /usr/bin/java java "${OPENJDK_HOME_ARM}/bin/java" 1000
	update-alternatives --install /usr/bin/javac javac "${OPENJDK_HOME_ARM}/bin/javac" 1000
	update-alternatives --set java "${OPENJDK_HOME_ARM}/bin/java"
	java -version

master:
	sh ./gradlew -Dbuild.snapshot=false assemble

PR-%:
	sh ./gradlew -Dbuild.snapshot=true assemble

oracle-%:
	# Production builds are made from oracle-x.y.z branches
	sh ./gradlew -Dbuild.snapshot=false assemble

user-dev:
	# Local ocibuild
	sh ./gradlew -Dbuild.snapshot=true assemble

output:
	mkdir -p output/ml-commons-os-plugin
	cp plugin/build/distributions/*.zip output/ml-commons-os-plugin

output-arm:
	mkdir -p output/ml-commons-os-plugin/arm
	cp plugin/build/distributions/*.zip output/ml-commons-os-plugin/arm