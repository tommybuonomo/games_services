package com.abedalkareem.games_services

import android.app.Activity
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.Games
import com.google.android.gms.games.LeaderboardsClient
import android.widget.Toast;
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

private const val CHANNEL_NAME = "games_services"

class GamesServicesPlugin(private var activity: Activity? = null) : FlutterPlugin, MethodCallHandler, ActivityAware {

    //region Variables
    private var googleSignInClient: GoogleSignInClient? = null
    private var achievementClient: AchievementsClient? = null
    private var leaderboardsClient: LeaderboardsClient? = null

    private var channel: MethodChannel? = null
    //endregion

    private fun explicitSignIn() {
        val activity = activity ?: return
        val builder = GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        builder.requestEmail()
        googleSignInClient = GoogleSignIn.getClient(activity, builder.build())
        activity?.startActivityForResult(googleSignInClient?.signInIntent, 0);
    }

    //region SignIn
    private fun silentSignIn(result: Result) {
        val activity = activity ?: return
        val builder = GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        builder.requestEmail();
        googleSignInClient = GoogleSignIn.getClient(activity, builder.build())
        googleSignInClient?.silentSignIn()?.addOnCompleteListener { task ->
            try {
                val googleSignInAccount = task.result
                if (task.isSuccessful && googleSignInAccount != null) {
                    achievementClient = Games.getAchievementsClient(activity, googleSignInAccount)
                    leaderboardsClient = Games.getLeaderboardsClient(activity, googleSignInAccount)
                    result.success("success")
                } else {
                    Log.e("Error", "signInError", task.exception)
                    result.error("error", task.exception?.message ?: "", null)
                }
            }catch(ex: Exception) {
                explicitSignIn();
                result.success("success");

            }
        }
    }
    //endregion

    //region Achievements
    private fun showAchievements(result: Result) {
        achievementClient?.achievementsIntent?.addOnSuccessListener { intent ->
            activity?.startActivityForResult(intent, 0)
            result.success("success")
        }?.addOnFailureListener {
            result.error("error", "${it.message}", null)
        }
    }

    private fun unlock(achievementID: String, result: Result) {
        achievementClient?.unlock(achievementID)
        result.success("success")
    }
    //endregion

    //region Leaderboards
    private fun showLeaderboards(result: Result) {
        if (leaderboardsClient?.allLeaderboardsIntent == null) {
            Toast.makeText(activity, "Please log to Google Play Games", Toast.LENGTH_LONG).show();
        }
        leaderboardsClient?.allLeaderboardsIntent?.addOnSuccessListener { intent ->
            activity?.startActivityForResult(intent, 0)
            result.success("success")
        }?.addOnFailureListener {
            result.error("error", "${it.message}", null)
        }
    }

    private fun submitScore(leaderboardID: String, score: Int, result: Result) {
        leaderboardsClient?.submitScore(leaderboardID, score.toLong())
        result.success("success")
    }
    //endregion

    //region FlutterPlugin
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        setupChannel(binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        teardownChannel()
    }

    private fun setupChannel(messenger: BinaryMessenger) {
        channel = MethodChannel(messenger, CHANNEL_NAME)
        val handler = GamesServicesPlugin(activity)
        channel?.setMethodCallHandler(handler)
    }

    private fun teardownChannel() {
        channel?.setMethodCallHandler(null)
        channel = null
    }
    //endregion

    //region ActivityAware
    override fun onDetachedFromActivity() {
        teardownChannel()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity

        // if channel was already created update it
        if (channel != null) {
            val handler = GamesServicesPlugin(binding.activity)
            channel?.setMethodCallHandler(handler)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }
    //endregion

    //region MethodCallHandler
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "unlock" -> {
                unlock(call.argument<String>("achievementID") ?: "", result)
            }
            "submitScore" -> {
                val leaderboardID = call.argument<String>("leaderboardID") ?: ""
                val score = call.argument<Int>("value") ?: 0
                submitScore(leaderboardID, score, result)
            }
            "showLeaderboards" -> {
                showLeaderboards(result)
            }
            "showAchievements" -> {
                showAchievements(result)
            }
            "silentSignIn" -> {
                silentSignIn(result)
            }
            else -> result.notImplemented()
        }
    }

    //endregion

}
