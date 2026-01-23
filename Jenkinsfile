/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  useContainerAgent: true,
  configurations: [
    [platform: 'linux', jdk: 25],
    [platform: 'windows', jdk: 21],
    // to validate it works with java 17 for jenkins lts
    [platform: 'linux', jdk: 17],
])
