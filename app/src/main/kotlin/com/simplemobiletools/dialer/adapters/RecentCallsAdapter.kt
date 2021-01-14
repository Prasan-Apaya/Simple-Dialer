package com.simplemobiletools.dialer.adapters

import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.dialogs.ShowGroupedCallsDialog
import com.simplemobiletools.dialer.extensions.areMultipleSIMsAvailable
import com.simplemobiletools.dialer.extensions.callContactWithSim
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall
import com.simplemobiletools.dialer.network.model.SpamUser
import kotlinx.android.synthetic.main.item_recent_call.view.*
import java.util.*
import kotlin.collections.ArrayList


class RecentCallsAdapter(activity: SimpleActivity, var recentCalls: ArrayList<RecentCall>,var spamCalls:ArrayList<SpamUser>, recyclerView: MyRecyclerView, val refreshItemsListener: RefreshItemsListener?,
                         itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    private var fontSize = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.md_red_700)
    private var textToHighlight = ""
    private val stringValues = arrayOf<String>("zxcv","12312","123133")

    init {
        initDrawables()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && activity.config.getCustomSIM(selectedNumber) != ""

            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected
            findItem(R.id.caller_info).isVisible = isOneItemSelected
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.cab_show_grouped_calls).isVisible = isOneItemSelected && selectedItems.first().neighbourIDs.isNotEmpty()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.caller_info -> getCallerInformation()
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_grouped_calls -> showGroupedCalls()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
        }
    }



    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_recent_call, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(recentCall, refreshItemsListener != null, refreshItemsListener != null) { itemView, layoutPosition ->
            setupView(itemView, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.item_recents_image)
        }
    }

    fun initDrawables() {
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, baseConfig.textColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, baseConfig.textColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, redColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(phoneNumber, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun askConfirmBlock() {
        val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock)

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)

            if (resolveActivity(activity.packageManager) != null) {
                activity.startActivity(this)
            } else {
                activity.toast(R.string.no_app_found)
            }
        }
    }


    private fun getCallerInformation() {
        val number = getSelectedItems().firstOrNull()?.phoneNumber
        val iterator = spamCalls.listIterator()
        for(item in iterator){
            if(item.phoneNumber == number){
                val alertDialog: AlertDialog.Builder = AlertDialog.Builder(activity)
                alertDialog.apply {
                    setTitle(context.getString(R.string.caller_info_title))
                    setMessage("Phone Number: ${item.phoneNumber}"+"\n"
                            +"Telecoms Provider: ${item.telecomProvider}"+"\n"
                            +"TeleMarketer: ${item.telemarketer}"+"\n"
                            +"SpamUser Name: ${item.username}" )

                    setPositiveButton("OK") { dialog, _ ->
                        dialog.cancel()
                    }
                    val dialog: AlertDialog = this.create()
                    dialog.show()
                }

                return
            }
        }
        activity.toast(R.string.not_spam)
    }

    private fun sendSMS() {
        val numbers = getSelectedItems().map {
            it.phoneNumber
        }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun showGroupedCalls() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val callIds = recentCall.neighbourIDs.map { it }.toMutableList() as ArrayList<Int>
        callIds.add(recentCall.id)
        ShowGroupedCallsDialog(activity, callIds)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            activity.runOnUiThread {
                if (recentCalls.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    fun updateItems(newItems: ArrayList<RecentCall>, highlightText: String = "") {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.clone() as ArrayList<RecentCall>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun getSelectedItems() = recentCalls.filter {
        selectedKeys.contains(it.id)
    } as ArrayList<RecentCall>

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun setupView(view: View, call: RecentCall) {
        view.apply {
            item_recents_frame.isSelected = selectedKeys.contains(call.id)
            var nameToShow = SpannableString(call.name)
            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow = SpannableString("$nameToShow (${call.neighbourIDs.size + 1})")
            }

            if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, adjustedPrimaryColor))
            }

            item_recents_name.apply {
                text = nameToShow
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            item_recents_date_time.apply {
                text = call.startTS.formatDateOrTime(context, refreshItemsListener != null)
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_duration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_sim_image.beVisibleIf(areMultipleSIMsAvailable)
            item_recents_sim_id.beVisibleIf(areMultipleSIMsAvailable)
            if (areMultipleSIMsAvailable) {
                item_recents_sim_image.applyColorFilter(textColor)
                item_recents_sim_id.setTextColor(textColor.getContrastColor())
                item_recents_sim_id.text = call.simID.toString()
            }

            SimpleContactsHelper(context).loadContactImage(call.photoUri, item_recents_image, call.name)

            val drawable = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallIcon
                Calls.MISSED_TYPE -> incomingMissedCallIcon
                else -> incomingCallIcon
            }

            item_recents_type.setImageDrawable(drawable)
        }
    }
}
