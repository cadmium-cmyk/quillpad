package org.qosp.notes.ui.gemini

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.qosp.notes.R
import org.qosp.notes.data.gemini.ChatMessage
import org.qosp.notes.data.gemini.GeminiService
import org.qosp.notes.databinding.DialogGeminiAssistantBinding
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.widget.WidgetUpdateHelper

class GeminiAssistantDialog : BottomSheetDialogFragment() {

    private var _binding: DialogGeminiAssistantBinding? = null
    private val binding get() = _binding!!

    private val activityModel: ActivityViewModel by activityViewModel()
    private val geminiService = GeminiService()

    private lateinit var chatAdapter: GeminiChatAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                val displayMetrics = resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.85).toInt()
                sheet.layoutParams.height = height
                sheet.requestLayout()
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGeminiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChatRecycler()
        setupListeners()
        setupChips()

        // Welcome initial message
        if (chatAdapter.messages.isEmpty()) {
            chatAdapter.addMessage(
                ChatMessage(
                    isUser = false,
                    text = "Hi! I'm your Gemini Notepad assistant. How can I help you today? Tap any of the ideas above or ask me to generate a checklist, recipe, or brainstorm ideas!"
                )
            )
        }
    }

    private fun setupChatRecycler() {
        chatAdapter = GeminiChatAdapter(
            onSaveChecklist = { title, items ->
                lifecycleScope.launch {
                    try {
                        val noteId = activityModel.createNoteWithTasks(title, items)
                        context?.let { ctx ->
                            WidgetUpdateHelper.updateAllWidgets(ctx)
                            Toast.makeText(ctx, getString(R.string.note_created), Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving note: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSaveNote = { title, content ->
                lifecycleScope.launch {
                    try {
                        val noteId = activityModel.createTextNote(title, content)
                        context?.let { ctx ->
                            WidgetUpdateHelper.updateAllWidgets(ctx)
                            Toast.makeText(ctx, getString(R.string.note_created), Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving note: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupChips() = with(binding) {
        chipGrocery.setOnClickListener {
            sendMessage("Generate a categorized grocery shopping list for a healthy week")
        }
        chipPacking.setOnClickListener {
            sendMessage("Create a complete travel packing checklist for a 5-day vacation")
        }
        chipTodo.setOnClickListener {
            sendMessage("Create a productive daily to-do checklist for work and personal goals")
        }
        chipBrainstorm.setOnClickListener {
            sendMessage("Give me 5 creative ideas for a personal project or productivity system")
        }
        chipMealPrep.setOnClickListener {
            sendMessage("Draft a 5-day dinner meal plan with quick healthy recipes")
        }
    }

    private fun setupListeners() = with(binding) {
        buttonClose.setOnClickListener { dismiss() }

        buttonSend.setOnClickListener {
            val query = editTextMessage.text.toString().trim()
            if (query.isNotEmpty()) {
                editTextMessage.setText("")
                sendMessage(query)
            }
        }
    }

    private fun sendMessage(prompt: String) {
        // Add user message to conversation
        chatAdapter.addMessage(ChatMessage(isUser = true, text = prompt))
        binding.recyclerChat.scrollToPosition(chatAdapter.messages.size - 1)

        binding.progressLoading.isVisible = true
        binding.buttonSend.isEnabled = false

        lifecycleScope.launch {
            val result = geminiService.generateText(chatAdapter.messages)
            binding.progressLoading.isVisible = false
            binding.buttonSend.isEnabled = true

            result.onSuccess { reply ->
                val (title, items) = GeminiService.parseListFromText(reply)
                val isList = items.size >= 2 || reply.contains("- [ ]") || reply.contains("- ")
                chatAdapter.addMessage(
                    ChatMessage(
                        isUser = false,
                        text = reply,
                        isList = isList,
                        listTitle = if (isList) title else null,
                        listItems = if (isList) items else emptyList()
                    )
                )
                binding.recyclerChat.scrollToPosition(chatAdapter.messages.size - 1)
            }.onFailure { error ->
                chatAdapter.addMessage(
                    ChatMessage(
                        isUser = false,
                        text = "Error: ${error.message ?: "Failed to generate response"}",
                        isError = true
                    )
                )
                binding.recyclerChat.scrollToPosition(chatAdapter.messages.size - 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GeminiAssistantDialog"
        fun newInstance() = GeminiAssistantDialog()
    }
}
