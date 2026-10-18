package org.qosp.notes.ui.gemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.qosp.notes.R
import org.qosp.notes.data.gemini.ChatMessage
import org.qosp.notes.databinding.LayoutGeminiMessageBinding

class GeminiChatAdapter(
    private val onSaveChecklist: (title: String, items: List<String>) -> Unit,
    private val onSaveNote: (title: String, content: String) -> Unit,
) : RecyclerView.Adapter<GeminiChatAdapter.MessageViewHolder>() {

    val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(message: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = message
            notifyItemChanged(messages.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = LayoutGeminiMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    inner class MessageViewHolder(
        private val binding: LayoutGeminiMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) = with(binding) {
            val context = itemView.context

            if (message.isUser) {
                containerUserMessage.isVisible = true
                containerBotMessage.isVisible = false
                textUserMessage.text = message.text
            } else {
                containerUserMessage.isVisible = false
                containerBotMessage.isVisible = true
                textBotMessage.text = message.text

                // If detected as a list, show action buttons to create checklist or text note
                val showActions = message.isList && message.listItems.isNotEmpty()
                layoutListActions.isVisible = showActions

                if (showActions) {
                    val title = message.listTitle ?: context.getString(R.string.chip_grocery_list)
                    buttonCreateChecklist.setOnClickListener {
                        onSaveChecklist(title, message.listItems)
                    }
                    buttonCreateNote.setOnClickListener {
                        onSaveNote(title, message.text)
                    }
                }

                buttonCopy.setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Gemini", message.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
