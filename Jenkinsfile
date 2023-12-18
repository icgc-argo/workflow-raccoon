@Library(value='jenkins-pipeline-library@master', changelog=false) _
pipelineRDPCWorkflowRaccoon(
    buildImage: "openjdk:11",
    dockerRegistry: "ghcr.io",
    dockerRepo: "icgc-argo/workflow-raccoon",
    gitRepo: "icgc-argo/workflow-raccoon",
    testCommand: "./mvnw test -ntp",
    helmRelease: "raccoon"
)
