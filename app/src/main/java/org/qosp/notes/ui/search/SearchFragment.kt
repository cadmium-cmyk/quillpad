package org.qosp.notes.ui.search

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.databinding.FragmentSearchBinding
import org.qosp.notes.databinding.LayoutNoteBinding
import org.qosp.notes.ui.common.AbstractNotesFragment
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.utils.requestFocusAndKeyboard
import org.qosp.notes.ui.utils.viewBinding

class SearchFragment : AbstractNotesFragment(resId = R.layout.fragment_search) {
    private val binding by viewBinding(FragmentSearchBinding::bind)
    private val args: SearchFragmentArgs by navArgs()

    override val currentDestinationId: Int = R.id.fragment_search
    override val model: SearchViewModel by viewModel()

    override val isSelectionEnabled = false
    override val hasMenu: Boolean = false

    override val recyclerView: RecyclerView
        get() = binding.recyclerSearch
    override val swipeRefreshLayout: SwipeRefreshLayout
        get() = binding.layoutSwipeRefresh
    override val emptyIndicator: View
        get() = binding.indicatorNotesEmpty
    override val toolbar: Toolbar
        get() = binding.toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerAdapter.searchMode = true

        binding.editTextSearch.doAfterTextChanged { text ->
            model.setSearchQuery(text.toString())
        }

        when {
            !model.isFirstLoad -> return
            args.searchQuery.isNotEmpty() -> {
                binding.editTextSearch.setText(args.searchQuery)
                binding.editTextSearch.requestFocusAndMoveCaret()
            }
            binding.editTextSearch.text?.isEmpty() == true -> {
                binding.editTextSearch.requestFocusAndKeyboard()
            }
        }

        model.isFirstLoad = false
    }

    override fun onNoteClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding) {
        val note = recyclerAdapter.getItemAtPosition(position)
        if (note.isHidden && !activityModel.isNoteUnlocked(noteId)) {
            org.qosp.notes.ui.utils.BiometricAuthManager.authenticate(
                fragment = this,
                title = getString(R.string.unlock_note_title),
                subtitle = getString(R.string.unlock_note_subtitle),
                onSuccess = {
                    activityModel.unlockNote(noteId)
                    applyNavToEditorAnimation(position)
                    findNavController().navigateSafely(
                        SearchFragmentDirections.actionSearchToEditor("editor_$noteId").setNoteId(noteId),
                        FragmentNavigatorExtras(viewBinding.root to "editor_$noteId")
                    )
                },
                onError = { error ->
                    android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            applyNavToEditorAnimation(position)
            findNavController().navigateSafely(
                SearchFragmentDirections.actionSearchToEditor("editor_$noteId").setNoteId(noteId),
                FragmentNavigatorExtras(viewBinding.root to "editor_$noteId")
            )
        }
    }

    override fun onNoteLongClick(noteId: Long, position: Int, viewBinding: LayoutNoteBinding): Boolean {
        showMenuForNote(position, isSelectionEnabled = false)
        return true
    }

    override fun onNotesChanged(notes: List<Note>) {
        binding.indicatorTextView.text =
            if (notes.isEmpty() && binding.editTextSearch.text?.isNotEmpty() == true) getString(R.string.indicator_no_results_found)
            else getString(R.string.indicator_search_empty)

        if (binding.editTextSearch.text?.isNotEmpty() == true)
            recyclerAdapter.submitList(notes)
        else
            recyclerAdapter.submitList(listOf())
    }
}
