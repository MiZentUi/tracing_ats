FROM liquibase/liquibase:4.29

# Changelog целиком попадает в /liquibase/changelog; include-пути внутри
# db.changelog-master.yaml относительны этого каталога.
COPY . /liquibase/changelog/

WORKDIR /liquibase
