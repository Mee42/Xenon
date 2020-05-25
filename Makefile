


base:
	@echo "run \"make install\" to install"


docs:
	asciidoctor docs/*.adoc
	asciidoctor --backend=manpage docs/help.adoc

installManPages: docs/xenon.1
	install -m 644 docs/xenon.1 /usr/share/man/man1/

docs/xenon.1:
	@echo run \"make docs\" before installing the man pages
	@exit 1


clean:
	rm -f docs/xenon.1
	rm -f docs/*.html
	rm -fr out/
	rm -fr gen/
	rm -fr build/
	rm -fr a.out

build:
	gradlew shadowJar

baseInstall:
	install -m 755 -d /opt/xenon/
	install -m 755 ./build/libs/xenon-v0.0.1-all.jar /opt/xenon/xenon.jar
	install -m 755 xenon.bin /bin/xenon

uninstall:
	rm -rf /opt/xenon
	rm -f /bin/xenon

install: build/libs/xenon-v0.0.1-all.jar installManPages baseInstall


build/libs/xenon-v0.0.1-all.jar:
	@echo run \"make build\" before installing
	@exit 1

.PHONY: base docs clean build installManPages baseInstall install uninstall