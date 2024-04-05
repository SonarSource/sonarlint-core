ARG CIRRUS_AWS_ACCOUNT
ARG JDK_VERSION
FROM ${CIRRUS_AWS_ACCOUNT}.dkr.ecr.eu-central-1.amazonaws.com/base:j${JDK_VERSION}-m3-latest

USER root

ENV NODE_VERSION=18
RUN apt-get update && apt-get install -y nodejs=${NODE_VERSION}.*

USER sonarsource
