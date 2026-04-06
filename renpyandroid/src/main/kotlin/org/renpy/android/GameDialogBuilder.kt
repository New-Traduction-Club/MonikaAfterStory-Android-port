package org.renpy.android

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Custom styled dialog builder that matches the launcher's visual theme.
 * Drop-in replacement for MaterialAlertDialogBuilder with the same API.
 */
class GameDialogBuilder(private val context: Context) {

    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var customView: View? = null
    private var positiveText: CharSequence? = null
    private var negativeText: CharSequence? = null
    private var positiveListener: DialogInterface.OnClickListener? = null
    private var negativeListener: DialogInterface.OnClickListener? = null
    private var cancelable: Boolean = true

    // List support
    private var items: Array<out CharSequence>? = null
    private var itemsListener: DialogInterface.OnClickListener? = null

    // Single-choice support
    private var singleChoiceItems: Array<out CharSequence>? = null
    private var checkedItem: Int = -1
    private var singleChoiceListener: DialogInterface.OnClickListener? = null

    fun setTitle(title: CharSequence): GameDialogBuilder {
        this.title = title
        return this
    }

    fun setTitle(titleRes: Int): GameDialogBuilder {
        this.title = context.getString(titleRes)
        return this
    }

    fun setMessage(message: CharSequence): GameDialogBuilder {
        this.message = message
        return this
    }

    fun setMessage(messageRes: Int): GameDialogBuilder {
        this.message = context.getString(messageRes)
        return this
    }

    fun setView(view: View): GameDialogBuilder {
        this.customView = view
        return this
    }

    fun setPositiveButton(text: CharSequence, listener: DialogInterface.OnClickListener?): GameDialogBuilder {
        this.positiveText = text
        this.positiveListener = listener
        return this
    }

    fun setNegativeButton(text: CharSequence, listener: DialogInterface.OnClickListener?): GameDialogBuilder {
        this.negativeText = text
        this.negativeListener = listener
        return this
    }

    fun setCancelable(cancelable: Boolean): GameDialogBuilder {
        this.cancelable = cancelable
        return this
    }

    fun setItems(items: Array<out CharSequence>, listener: DialogInterface.OnClickListener): GameDialogBuilder {
        this.items = items
        this.itemsListener = listener
        return this
    }

    fun setSingleChoiceItems(items: Array<out CharSequence>, checkedItem: Int, listener: DialogInterface.OnClickListener): GameDialogBuilder {
        this.singleChoiceItems = items
        this.checkedItem = checkedItem
        this.singleChoiceListener = listener
        return this
    }

    fun create(): AlertDialog {
        SoundEffects.initialize(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_game, null)

        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val customContainer = dialogView.findViewById<FrameLayout>(R.id.dialogCustomContainer)
        val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
        val buttonRow = dialogView.findViewById<View>(R.id.dialogButtonRow)
        val positiveButton = dialogView.findViewById<TextView>(R.id.dialogPositiveButton)
        val negativeButton = dialogView.findViewById<TextView>(R.id.dialogNegativeButton)

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)
        builder.setCancelable(cancelable)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Title
        if (title != null) {
            titleView.text = title
            titleView.visibility = View.VISIBLE
        }

        // Message
        if (message != null) {
            messageView.text = message
            messageView.visibility = View.VISIBLE
        }

        // Custom view
        if (customView != null) {
            // Remove from parent if already attached
            (customView?.parent as? ViewGroup)?.removeView(customView)
            customContainer.addView(customView)
            customContainer.visibility = View.VISIBLE
            customContainer.setPadding(
                dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(8)
            )
        }

        // Items list (simple list)
        if (items != null) {
            setupItemsList(listView, items!!, dialog)
        }

        // Single-choice items
        if (singleChoiceItems != null) {
            setupSingleChoiceList(listView, singleChoiceItems!!, dialog)
        }

        // Positive button
        if (positiveText != null) {
            positiveButton.text = positiveText
            positiveButton.visibility = View.VISIBLE
            positiveButton.setOnClickListener {
                SoundEffects.playClick(context)
                positiveListener?.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                dialog.dismiss()
            }
        }

        // Negative button
        if (negativeText != null) {
            negativeButton.text = negativeText
            negativeButton.visibility = View.VISIBLE
            negativeButton.setOnClickListener {
                SoundEffects.playClick(context)
                negativeListener?.onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
                dialog.dismiss()
            }
        }

        dialog.setOnShowListener {
            constrainScrollableContentHeight(
                dialogView = dialogView,
                titleView = titleView,
                messageView = messageView,
                buttonRow = buttonRow,
                customContainer = customContainer,
                listView = listView
            )
        }

        return dialog
    }

    fun show(): AlertDialog {
        val dialog = create()
        dialog.show()
        return dialog
    }

    private fun setupItemsList(listView: ListView, items: Array<out CharSequence>, dialog: AlertDialog) {
        listView.visibility = View.VISIBLE
        val adapter = object : ArrayAdapter<CharSequence>(context, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                textView.textSize = 14f
                textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.colorWindowContentBackground))
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            SoundEffects.playClick(context)
            itemsListener?.onClick(dialog, position)
            dialog.dismiss()
        }
    }

    private fun setupSingleChoiceList(listView: ListView, items: Array<out CharSequence>, dialog: AlertDialog) {
        listView.visibility = View.VISIBLE
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        val adapter = object : ArrayAdapter<CharSequence>(context, android.R.layout.simple_list_item_single_choice, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
                textView.textSize = 14f
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.colorWindowContentBackground))
                return view
            }
        }
        listView.adapter = adapter
        if (checkedItem >= 0) {
            listView.setItemChecked(checkedItem, true)
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            SoundEffects.playClick(context)
            singleChoiceListener?.onClick(dialog, position)
        }
    }

    private fun constrainScrollableContentHeight(
        dialogView: View,
        titleView: TextView,
        messageView: TextView,
        buttonRow: View,
        customContainer: FrameLayout,
        listView: ListView
    ) {
        dialogView.post {
            val availableWindowHeight = dialogView.rootView.height
                .takeIf { it > 0 }
                ?: context.resources.displayMetrics.heightPixels
            val maxDialogHeight = (availableWindowHeight * 0.85f).toInt()
            val fixedHeight = titleView.height + messageView.height + buttonRow.height +
                dialogView.paddingTop + dialogView.paddingBottom

            val scrollableViews = buildList<View> {
                if (customContainer.visibility == View.VISIBLE) add(customContainer)
                if (listView.visibility == View.VISIBLE) add(listView)
            }
            if (scrollableViews.isEmpty()) return@post

            val availableContentHeight = (maxDialogHeight - fixedHeight).coerceAtLeast(dpToPx(96))
            val maxHeightPerView = (availableContentHeight / scrollableViews.size)
                .coerceAtLeast(dpToPx(96))

            scrollableViews.forEach { target ->
                val params = target.layoutParams
                params.height = if (target.height > maxHeightPerView) {
                    maxHeightPerView
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                target.layoutParams = params
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
