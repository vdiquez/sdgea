package sgdea.platform.storage

// P-03: toda implementación self-hosted debe operar sin conectividad saliente (P-10).
interface ObjectStorage {
    fun put(key: String, content: ByteArray)
    fun get(key: String): ByteArray
    fun exists(key: String): Boolean
    fun delete(key: String)
}

// Backend gestionado (SaaS): proveedor concreto sin decidir todavía.
class ManagedObjectStorage : ObjectStorage {
    override fun put(key: String, content: ByteArray) = TODO()
    override fun get(key: String): ByteArray = TODO()
    override fun exists(key: String): Boolean = TODO()
    override fun delete(key: String) = TODO()
}

// Backend autoalojado (on-premise, P-10): debe funcionar sin conectividad saliente.
class SelfHostedObjectStorage : ObjectStorage {
    override fun put(key: String, content: ByteArray) = TODO()
    override fun get(key: String): ByteArray = TODO()
    override fun exists(key: String): Boolean = TODO()
    override fun delete(key: String) = TODO()
}
