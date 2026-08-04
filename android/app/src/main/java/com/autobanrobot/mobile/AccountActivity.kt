package com.autobanrobot.mobile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONObject

class AccountActivity : Activity() {
    private enum class Screen { LOGIN, REGISTER, RECOVERY }

    private lateinit var store: AccountStore
    private lateinit var root: LinearLayout
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var answer: EditText
    private lateinit var newPassword: EditText
    private lateinit var question: Spinner
    private lateinit var status: TextView
    private var recoveryQuestion: String? = null
    private var screen = Screen.LOGIN
    private val questionKeys = listOf("first_teacher", "childhood_nickname", "first_pet", "favorite_book", "favorite_food", "dream_job", "first_concert", "favorite_city", "childhood_friend", "favorite_film")
    private val questions by lazy { linkedMapOf(
        "first_teacher" to getString(R.string.security_first_teacher), "childhood_nickname" to getString(R.string.security_childhood_nickname), "first_pet" to getString(R.string.security_first_pet), "favorite_book" to getString(R.string.security_favorite_book), "favorite_food" to getString(R.string.security_favorite_food), "dream_job" to getString(R.string.security_dream_job), "first_concert" to getString(R.string.security_first_concert), "favorite_city" to getString(R.string.security_favorite_city), "childhood_friend" to getString(R.string.security_childhood_friend), "favorite_film" to getString(R.string.security_favorite_film)
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AccountStore(this, RuleStore(this))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setOnApplyWindowInsetsListener { _, insets ->
                setPadding(dp(20), insets.getInsets(WindowInsets.Type.statusBars()).top + dp(14), dp(20), dp(20))
                insets
            }
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.requestApplyInsets()
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = getString(R.string.account_title); textSize = 26f; setTextColor(Color.rgb(15, 20, 25))
        })
        root.addView(TextView(this).apply {
            text = when {
                store.session() != null -> getString(R.string.account_signed_in_intro)
                screen == Screen.REGISTER -> getString(R.string.account_register_intro)
                screen == Screen.RECOVERY -> getString(R.string.account_recovery_intro)
                else -> getString(R.string.account_subtitle)
            }
            textSize = 13f; setTextColor(Color.rgb(105, 116, 130)); setPadding(0, dp(8), 0, dp(22))
        })
        val session = store.session()
        if (session != null) renderSignedIn(session) else renderForm()
    }

    private fun renderSignedIn(session: AutoBanSession) {
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(18), dp(17), dp(18), dp(17))
            addView(TextView(this@AccountActivity).apply {
                text = session.username; textSize = 21f; setTextColor(Color.rgb(15, 20, 25)); setPadding(0, 0, 0, dp(4))
            })
            addView(TextView(this@AccountActivity).apply {
                text = getString(R.string.account_sync_detail); textSize = 13f; setTextColor(Color.rgb(105, 116, 130))
            })
        }.also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) } })
        root.addView(button(getString(R.string.account_sync_now), primary = true) { syncAccount() })
        root.addView(button(getString(R.string.account_logout)) { logout() })
        addStatus()
    }

    private fun renderForm() {
        username = field(getString(R.string.account_username))
        password = field(getString(R.string.account_password), true)
        answer = field(getString(R.string.account_security_answer), true)
        newPassword = field(getString(R.string.account_new_password), true)
        question = Spinner(this).apply {
            adapter = ArrayAdapter(this@AccountActivity, android.R.layout.simple_spinner_dropdown_item, questions.values.toList())
            setPadding(dp(16), 0, dp(12), 0)
            background = inputBackground(false)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(12) }
        }
        when (screen) {
            Screen.LOGIN -> {
                root.addView(username); root.addView(password)
                root.addView(button(getString(R.string.account_login), primary = true) { login() })
                root.addView(button(getString(R.string.account_register_prompt)) { screen = Screen.REGISTER; render() })
                root.addView(textButton(getString(R.string.account_forgot)) { screen = Screen.RECOVERY; render() })
            }
            Screen.REGISTER -> {
                root.addView(username); root.addView(password); root.addView(question); root.addView(answer)
                root.addView(button(getString(R.string.account_create), primary = true) { register() })
                root.addView(textButton(getString(R.string.account_back_login)) { screen = Screen.LOGIN; render() })
            }
            Screen.RECOVERY -> {
                root.addView(username)
                if (recoveryQuestion == null) {
                    root.addView(button(getString(R.string.account_verify_username), primary = true) { loadRecoveryQuestion() })
                } else {
                    question.setSelection(questionKeys.indexOf(recoveryQuestion).coerceAtLeast(0)); question.isEnabled = false
                    root.addView(question); root.addView(answer); root.addView(newPassword)
                    root.addView(button(getString(R.string.account_reset), primary = true) { resetPassword() })
                }
                root.addView(textButton(getString(R.string.account_return_login)) { recoveryQuestion = null; screen = Screen.LOGIN; render() })
            }
        }
        addStatus()
    }

    private fun addStatus() {
        status = TextView(this).apply {
            textSize = 13f; setTextColor(Color.rgb(92, 104, 118)); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(10), 0, 0)
        }
        root.addView(status)
    }

    private fun login() = authenticate("login", JSONObject().put("username", username.text.toString().trim()).put("password", password.text.toString()))
    private fun register() = authenticate("register", JSONObject().put("username", username.text.toString().trim()).put("password", password.text.toString()).put("securityQuestionKey", questionKeys[question.selectedItemPosition]).put("securityAnswer", answer.text.toString()))

    private fun authenticate(mode: String, payload: JSONObject) {
        status.text = getString(R.string.account_working)
        Thread {
            val result = store.authenticate(mode, payload)
            runOnUiThread {
                result.fold(onSuccess = { render() }, onFailure = { status.text = it.message ?: getString(R.string.account_action_failed) })
            }
        }.start()
    }

    private fun loadRecoveryQuestion() {
        status.text = getString(R.string.account_working)
        Thread {
            val result = store.recoveryQuestion(username.text.toString().trim())
            runOnUiThread {
                result.fold(onSuccess = { key -> recoveryQuestion = key; render() }, onFailure = { status.text = it.message ?: getString(R.string.account_recovery_failed) })
            }
        }.start()
    }

    private fun resetPassword() {
        val key = recoveryQuestion ?: return
        status.text = getString(R.string.account_working)
        val payload = JSONObject().put("username", username.text.toString().trim()).put("securityQuestionKey", key).put("securityAnswer", answer.text.toString()).put("newPassword", newPassword.text.toString())
        Thread {
            val result = store.authenticate("recovery/reset", payload)
            runOnUiThread { result.fold(onSuccess = { render() }, onFailure = { status.text = it.message ?: getString(R.string.account_reset_failed) }) }
        }.start()
    }

    private fun syncAccount() {
        status.text = getString(R.string.account_working)
        Thread {
            val result = store.bindAndMerge()
            runOnUiThread { status.text = result.fold(onSuccess = { getString(R.string.account_sync_done) }, onFailure = { it.message ?: getString(R.string.account_action_failed) }) }
        }.start()
    }

    private fun logout() {
        status.text = getString(R.string.account_working)
        Thread { store.logout(); runOnUiThread { screen = Screen.LOGIN; render() } }.start()
    }

    private fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
        this.hint = hint; textSize = 16f; setTextColor(Color.rgb(15, 20, 25)); setHintTextColor(Color.rgb(120, 130, 140))
        setPadding(dp(16), 0, dp(16), 0); minHeight = 0
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        background = inputBackground(false); setOnFocusChangeListener { _, focused -> background = inputBackground(focused) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(12) }
    }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; setTextColor(if (primary) Color.WHITE else Color.rgb(29, 107, 222))
        background = buttonBackground(primary); elevation = if (primary) dp(2).toFloat() else 0f; minHeight = 0; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { bottomMargin = dp(10) }
    }

    private fun textButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.rgb(29, 107, 222)); setPadding(0, dp(7), 0, dp(6)); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun inputBackground(focused: Boolean) = GradientDrawable().apply { setColor(Color.rgb(248, 250, 252)); cornerRadius = dp(14).toFloat(); setStroke(dp(if (focused) 2 else 1), if (focused) Color.rgb(29, 107, 222) else Color.rgb(218, 226, 234)) }
    private fun buttonBackground(primary: Boolean) = GradientDrawable().apply { setColor(if (primary) Color.rgb(29, 107, 222) else Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), if (primary) Color.rgb(29, 107, 222) else Color.rgb(188, 211, 239)) }
    private fun panelBackground() = GradientDrawable().apply { setColor(Color.rgb(245, 249, 255)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(208, 223, 242)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
