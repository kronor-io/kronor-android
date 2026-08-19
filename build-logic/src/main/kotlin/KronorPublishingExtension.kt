import org.gradle.api.provider.Property

abstract class KronorPublishingExtension {
    abstract val groupId: Property<String>
    abstract val artifactId: Property<String>
    abstract val displayName: Property<String>
    abstract val description: Property<String>
}
