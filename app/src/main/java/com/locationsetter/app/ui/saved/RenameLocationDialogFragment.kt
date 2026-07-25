package com.locationsetter.app.ui.saved

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.appcompat.app.AlertDialog
import com.locationsetter.app.R

class RenameLocationDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val id = requireArguments().getLong(ARG_ID)
        val currentName = requireArguments().getString(ARG_NAME).orEmpty()

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentName)
            setSelection(currentName.length)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_rename)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(RESULT_ID to id, RESULT_NAME to newName)
                    )
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        const val REQUEST_KEY = "rename_location_request"
        const val RESULT_ID = "result_id"
        const val RESULT_NAME = "result_name"
        private const val ARG_ID = "arg_id"
        private const val ARG_NAME = "arg_name"

        fun newInstance(id: Long, currentName: String): RenameLocationDialogFragment =
            RenameLocationDialogFragment().apply {
                arguments = bundleOf(ARG_ID to id, ARG_NAME to currentName)
            }
    }
}
