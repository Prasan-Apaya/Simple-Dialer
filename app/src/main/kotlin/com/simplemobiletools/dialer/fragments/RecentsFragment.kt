package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall
import com.simplemobiletools.dialer.network.RetrofitFactory
import com.simplemobiletools.dialer.network.model.SpamUser
import kotlinx.android.synthetic.main.fragment_recents.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allRecentCalls = ArrayList<RecentCall>()
    private lateinit var spamCallList: ArrayList<SpamUser>
    companion object{
        private const val TAG = "RecentsFragment"
    }



    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        recents_placeholder.text = context.getString(placeholderResId)
        recents_placeholder_2.apply {
            setTextColor(context.config.primaryColor)
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun textColorChanged(color: Int) {
        (recents_list?.adapter as? RecentCallsAdapter)?.apply {
            initDrawables()
            updateTextColor(color)
        }
    }

    override fun primaryColorChanged(color: Int) {}

    override fun refreshItems() {
        val privateCursor = context?.getMyContactsCursor()?.loadInBackground()
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
            SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumbers.first() == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                allRecentCalls = recents
                GlobalScope.launch(Dispatchers.Main){
                    val response = RetrofitFactory.spamUserService.getSpamUsers()
                    spamCallList = async(Dispatchers.IO) {
                        if (response.isSuccessful) return@async response.body()!!.spamUsers else null
                    }.await() as ArrayList<SpamUser>
                    Log.d(TAG, "setupFragment: $spamCallList")
                    gotRecents(recents,spamCallList)
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>,spamList:ArrayList<SpamUser>) {
        if (recents.isEmpty()) {
            recents_placeholder.beVisible()
            recents_placeholder_2.beVisibleIf(!context.hasPermission(PERMISSION_READ_CALL_LOG))
            recents_list.beGone()
        } else {
            recents_placeholder.beGone()
            recents_placeholder_2.beGone()
            recents_list.beVisible()

            val currAdapter = recents_list.adapter
            if (currAdapter == null) {
                RecentCallsAdapter(activity as SimpleActivity, recents,spamList, recents_list, this) {
                    activity?.launchCallIntent((it as RecentCall).phoneNumber)
                }.apply {
                    recents_list.adapter = this
                }
            } else {
                (currAdapter as RecentCallsAdapter).updateItems(recents)
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                recents_placeholder.text = context.getString(R.string.no_previous_calls)
                recents_placeholder_2.beGone()

                val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
                RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents,spamCallList)
                    }
                }
            }
        }
    }

    override fun onSearchClosed() {
        recents_placeholder.beVisibleIf(allRecentCalls.isEmpty())
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val recentCalls = allRecentCalls.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.toMutableList() as ArrayList<RecentCall>

        recents_placeholder.beVisibleIf(recentCalls.isEmpty())
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(recentCalls, text)
    }
}
