# Stratio
#
# VERSION       0.1.0

FROM qa.stratio.com/stratio/spark-krb-dispatcher-support:2.1.0
MAINTAINER Sparta Team "sparta@stratio.com"

COPY docker-entrypoint.sh /docker-entrypoint.sh

ADD http://tools.stratio.com/mesos/mesos-0.22.1/stratio-mesos_0.22.1_amd64.deb .

RUN apt-get update && apt-get install -y wget libsvn1 libcurl4-nss-dev default-jre-headless docker \
 	&& dpkg -i /stratio-mesos_0.22.1_amd64.deb \
    && rm -rf /stratio-mesos_0.22.1_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

ENV SPARK_HOME=/opt/spark/dist

RUN chmod a+x /docker-entrypoint.sh

EXPOSE 5050 7070

ENTRYPOINT [ "/docker-entrypoint.sh" ]
