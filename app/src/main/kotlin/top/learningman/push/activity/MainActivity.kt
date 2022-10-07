package top.learningman.push.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder
import top.learningman.push.R
import top.learningman.push.application.MainApplication
import top.learningman.push.data.Repo
import top.learningman.push.databinding.ActivityMainBinding
import top.learningman.push.provider.AutoChannel
import top.learningman.push.provider.Channel
import top.learningman.push.utils.APIUtils
import top.learningman.push.utils.PermissionManager

class MainActivity : AppCompatActivity() {

    private val repo by lazy {
        (application as MainApplication).repo
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var channel: Channel

    private val startSetting =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("MainActivity", "Setting Activity Result ${it.resultCode}")
            if ((it.resultCode and SettingsActivity.UPDATE_CHANNEL) == SettingsActivity.UPDATE_CHANNEL) {
                Log.d("MainActivity", "Update Channel")
                val channel = AutoChannel.getInstance(context = this, nocache = true)
                channel.init(context = this)
            } else if ((it.resultCode and SettingsActivity.UPDATE_USERNAME) == SettingsActivity.UPDATE_USERNAME) {
                Log.d("MainActivity", "Update User")
            }

            if (it.resultCode != 0) {
                refreshStatus()
                channel.setUserCallback(this, repo.getUser(), lifecycleScope)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        channel = AutoChannel.getInstance(this)
        if (!PermissionManager(this).ok()) {
            startActivity(Intent(this, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            })
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.goSetting.setOnClickListener {
            startSetting.launch(Intent(this, SettingsActivity::class.java))
        }

        binding.goHistory.setOnClickListener {
            if (status == RegStatus.SUCCESS) {
                startActivity(Intent(this, MessagesActivity::class.java))
            } else {
                Log.w("MainActivity", "Not registered $status")
                Toast.makeText(this, "请输入正确用户名", Toast.LENGTH_SHORT).show()
            }
        }

        val userid = repo.getUser()

        if (userid == Repo.PREF_USER_DEFAULT) {
            setStatus(RegStatus.NOT_SET)
        } else {
            refreshStatus()
        }
        channel.init(this)
    }

    override fun onResume() {
        super.onResume()
    }

    private fun refreshStatus(){
        setStatus(RegStatus.PENDING)

        lifecycleScope.launch {
            APIUtils.check(repo.getUser())
                .onSuccess {
                    setStatus(RegStatus.SUCCESS)
                }
                .onFailure {
                    setStatus(RegStatus.FAILED)
                }
        }
    }

    private var status = RegStatus.NOT_SET
    private fun setStatus(status: RegStatus) {
        this.status = status
        val statusMap = mapOf(
            RegStatus.SUCCESS to listOf(
                R.color.reg_success,
                R.string.userid_success,
                MaterialDrawableBuilder.IconValue.CHECK
            ),
            RegStatus.PENDING to listOf(
                R.color.reg_pending,
                R.string.loading,
                MaterialDrawableBuilder.IconValue.SYNC
            ),
            RegStatus.FAILED to listOf(
                R.color.reg_failed,
                R.string.connect_err,
                MaterialDrawableBuilder.IconValue.SYNC_ALERT
            ),
            RegStatus.NOT_SET to listOf(
                R.color.reg_failed,
                R.string.not_set_userid_err,
                MaterialDrawableBuilder.IconValue.ALERT_CIRCLE_OUTLINE
            )
        )
        runOnUiThread {
            val (color, text, icon) = statusMap[status]!!
            binding.regStatusText.text = getString(text as Int)
            binding.regStatus.setCardBackgroundColor(this.colorList(color as Int))
            binding.regStatusIcon.setIcon(icon as MaterialDrawableBuilder.IconValue)
        }
    }

    private fun Context.colorList(id: Int): ColorStateList {
        return ColorStateList.valueOf(ContextCompat.getColor(this, id))
    }


    companion object {

        enum class RegStatus {
            SUCCESS,
            PENDING,
            FAILED,
            NOT_SET
        }
    }
}