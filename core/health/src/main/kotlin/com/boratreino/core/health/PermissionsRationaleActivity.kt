package com.boratreino.core.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Explains what Health Connect access is used for. Plain Android, no extra libraries. */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 24).toInt()
        val title = TextView(this).apply {
            setText(R.string.health_rationale_title)
            textSize = 22f
        }
        val body = TextView(this).apply {
            setText(R.string.health_rationale_text)
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val policy = Button(this).apply {
            setText(R.string.health_privacy_policy)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(body, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(policy, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(root)
    }

    private companion object {
        const val PRIVACY_POLICY_URL = "https://ramonvidal.github.io/BoraTreino/privacy-policy.html"
    }
}
