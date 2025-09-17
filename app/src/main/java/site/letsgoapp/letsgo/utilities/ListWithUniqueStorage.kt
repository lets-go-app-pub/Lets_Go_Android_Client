package site.letsgoapp.letsgo.utilities

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.function.Predicate
import java.util.function.UnaryOperator

//this assumes that each K is unique with respect to E (for example each ChatRoomObject has a unique chat room Id)
//alternative to a LinkedHashSet or LinkedHashMap
class ListWithUniqueStorage<K, E>(val getKeyFromValue: (E) -> K) : ArrayList<E>() {
    private val mutableSet = mutableSetOf<K>()

    fun move(initialIndex: Int, finalIndex: Int) {
        //NOTE: move() is its own function because the set makes things more complex when trying
        // to move items.
        val ele = this[initialIndex]
        super.removeAt(initialIndex)
        super.add(
            finalIndex,
            ele
        )
    }

    override fun add(element: E): Boolean {
        val key = getKeyFromValue(element)
        return if (!mutableSet.contains(key)) {
            mutableSet.add(key)
            super.add(element)
        } else {
            false
        }
    }

    override fun add(index: Int, element: E) {
        val key = getKeyFromValue(element)
        if (!mutableSet.contains(key)) {
            mutableSet.add(key)
            super.add(index, element)
        }
    }

    override fun addAll(elements: Collection<E>): Boolean {
        val elementsToAdd = mutableListOf<E>()
        for (x in elements) {
            val key = getKeyFromValue(x)
            if (!mutableSet.contains(key)) {
                mutableSet.add(key)
                elementsToAdd.add(x)
            }
        }
        return super.addAll(elementsToAdd)
    }

    override fun addAll(
        index: Int,
        elements: Collection<E>
    ): Boolean {
        val elementsToAdd = mutableListOf<E>()
        for (x in elements) {
            val key = getKeyFromValue(x)
            if (!mutableSet.contains(key)) {
                mutableSet.add(key)
                elementsToAdd.add(x)
            }
        }
        return super.addAll(index, elementsToAdd)
    }

    override fun remove(element: E): Boolean {
        mutableSet.remove(getKeyFromValue(element))
        return super.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        for (x in elements) {
            mutableSet.remove(getKeyFromValue(x))
        }
        return super.removeAll(elements)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun removeIf(filter: Predicate<in E>): Boolean {
        for (x in this)
            if (filter.test(x))
                mutableSet.remove(getKeyFromValue(x))
        return super.removeIf(filter)
    }

    override fun removeAt(index: Int): E {
        mutableSet.remove(getKeyFromValue(this[index]))
        return super.removeAt(index)
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        val elementsToRemove = mutableListOf<K>()
        for (i in fromIndex..toIndex) {
            elementsToRemove.add(getKeyFromValue(this[i]))
        }
        mutableSet.removeAll(elementsToRemove)
        super.removeRange(fromIndex, toIndex)
    }

    override fun clear() {
        mutableSet.clear()
        super.clear()
    }

    override fun set(index: Int, element: E): E {
        val prevKey = getKeyFromValue(this[index])
        val newKey = getKeyFromValue(element)
        if (prevKey != newKey) {
            mutableSet.remove(prevKey)
            mutableSet.add(newKey)
        }
        return super.set(index, element)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun replaceAll(operator: UnaryOperator<E>) {
        super.replaceAll(operator)
        mutableSet.clear()
        //inefficient but not using the function atm
        for (x in this) mutableSet.add(getKeyFromValue(x))
    }
}
