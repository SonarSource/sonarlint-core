import java.util.zip.ZipEntry
import java.util.zip.ZipFile

println 'Ensuring dependencies are compiled to Java 8 or older...'

def dependencyTreeBuilder = container.lookup("org.apache.maven.shared.dependency.tree.DependencyTreeBuilder")
def localRepository = session.getLocalRepository()

def readMajorVersion(ZipFile zipFile, ZipEntry zipEntry) {
    new DataInputStream(zipFile.getInputStream(zipEntry)).with({ inputStream ->
        try {
            if (inputStream.skip(7) == 7) {
                inputStream.read()
            } else {
                throw new IllegalStateException("Could not read major version from ${zipEntry.name}")
            }
        } finally {
            inputStream.close()
        }
    })
}

def visitDependencyTree(indent, node) {
    def artifact = node.getArtifact()
    if (node.getParent() != null) {
        def artifactDesc = "${artifact.getScope()} ${artifact.getGroupId()}:${artifact.getArtifactId()}:${artifact.getVersion()}:${artifact.getClassifier()}"
//        println("${indent}${artifactDesc} => ${artifact.getFile()}")

        if (artifact.getFile() != null) {
            new ZipFile(artifact.getFile()).with({ zipFile ->
                def onlyJava8 = true
                try {
                    zipFile.entries()
                        .findAll({ !it.isDirectory() && it.name.endsWith(".class") && !it.name.startsWith("META-INF/versions/") && !it.name.endsWith("module-info.class") })
                        .each({zipEntryFile ->
                            def javaMajorVersion = readMajorVersion(zipFile, zipEntryFile)
                            if (javaMajorVersion > 52) {
                                println "${zipEntryFile.name} has been compiled to a more recent version of Java than 8 (java8=52, java11=55, actual=${javaMajorVersion})"
                                onlyJava8 = false
                            }
                        })

                    if (!onlyJava8) {
                        throw new IllegalStateException("Dependency ${artifactDesc} contains class file(s) compiled to a Java version > Java 8 (see logs for details)")
                    }
                } finally {
                    zipFile.close()
                }
            })
        }
    }

    node.getChildren().each({
        child -> visitDependencyTree(indent + " ", child)
    })
}

def rootNode = dependencyTreeBuilder.buildDependencyTree(project, localRepository, new org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter("runtime"))
visitDependencyTree("", rootNode)
