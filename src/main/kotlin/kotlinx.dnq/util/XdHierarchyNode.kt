package kotlinx.dnq.util

import com.jetbrains.teamsys.dnq.database.BasePersistentClassImpl
import javassist.util.proxy.ProxyFactory
import javassist.util.proxy.ProxyObject
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdEntityType
import kotlinx.dnq.XdLegacyEntityType
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.link.XdLink
import kotlinx.dnq.simple.XdConstrainedProperty
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties


class XdHierarchyNode(val entityType: XdEntityType<*>, val parentNode: XdHierarchyNode?) {
    companion object {
        private val DELEGATE = Regex("(\\w+)\\\$delegate")
    }

    interface MetaProperty {
        val property: KProperty1<*, *>
        val dbPropertyName: String
    }

    data class SimpleProperty(override val property: KProperty1<*, *>, val delegate: XdConstrainedProperty<*, *>) : MetaProperty {
        override val dbPropertyName: String
            get() = delegate.dbPropertyName ?: property.name
    }

    data class LinkProperty(override val property: KProperty1<*, *>, val delegate: XdLink<*, *>) : MetaProperty {
        override val dbPropertyName: String
            get() = delegate.dbPropertyName ?: property.name
    }

    val entityConstructor = entityType.entityConstructor

    val children = mutableListOf<XdHierarchyNode>()
    val simpleProperties = LinkedHashMap<String, SimpleProperty>()
    val linkProperties = LinkedHashMap<String, LinkProperty>()

    init {
        parentNode?.children?.add(this)

        val ctor = entityConstructor
        if (ctor != null) {
            initProperties(ctor(FakeEntity))
        }
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(${entityType.entityType})"
    }

    private var arePropertiesInited = false

    private fun initProperties(xdFakeEntity: XdEntity) {
        if (arePropertiesInited) return
        parentNode?.initProperties(xdFakeEntity)

        arePropertiesInited = true

        val xdEntityClass = this.entityType.javaClass.enclosingClass
        xdEntityClass.getDelegatedFields().forEach {
            val (property, delegateField) = it
            val delegate = delegateField.get(xdFakeEntity)
            when (delegate) {
                is XdConstrainedProperty<*, *> -> simpleProperties[property.name] = SimpleProperty(property, delegate)
                is XdLink<*, *> -> linkProperties[property.name] = LinkProperty(property, delegate)
            }
        }
    }

    val naturalPersistentClassInstance by lazy {
        val xdEntityType = entityType as? XdNaturalEntityType<*>
                ?: throw UnsupportedOperationException("This property is available only for XdNaturalEntityType")

        val persistentClass = this.findLegacyEntitySuperclass()?.legacyClass ?: CommonBasePersistentClass::class.java

        ProxyFactory().apply {
            superclass = persistentClass
            setFilter { isNotFinalize(it) }
            isUseCache = false
        }.create(emptyArray(), emptyArray()).apply {
            this as ProxyObject
            handler = PersistentClassMethodHandler(this, xdEntityType)
        } as BasePersistentClassImpl
    }

    private fun findLegacyEntitySuperclass(): XdLegacyEntityType<*, *>? = this.entityType.let {
        it as? XdLegacyEntityType<*, *> ?: this.parentNode?.findLegacyEntitySuperclass()
    }

    private fun isNotFinalize(method: Method) = !method.parameterTypes.isEmpty() || method.name != "finalize"

    fun resolveMetaProperty(prop: KProperty1<*, *>): MetaProperty? {
        return simpleProperties[prop.name]
                ?: linkProperties[prop.name]
                ?: this.parentNode?.resolveMetaProperty(prop)
    }

    private fun <T : Any> Class<T>.getDelegatedFields(): List<Pair<KProperty1<*, *>, Field>> {
        return this.declaredFields.mapNotNull { field ->
            field.isAccessible = true
            val matchEntire = DELEGATE.matchEntire(field.name)
            if (matchEntire != null) {
                val (propertyName) = matchEntire.destructured
                val property = kotlin.declaredMemberProperties.firstOrNull { it.name == propertyName }
                if (property != null) {
                    Pair(property, field)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }
}

