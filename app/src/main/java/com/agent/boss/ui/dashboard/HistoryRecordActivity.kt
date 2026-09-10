package com.agent.boss.ui.dashboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agent.boss.data.local.AppDatabase
import com.agent.boss.data.local.entity.JobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史投递与大模型评估记录看板
 */
class HistoryRecordActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTv: TextView
    private val records = mutableListOf<JobEntity>()
    private val adapter = HistoryAdapter(records)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "岗位投递与评估流水"
        buildUi()
        loadData()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121214"))
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryRecordActivity)
            adapter = this@HistoryRecordActivity.adapter
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        emptyTv = TextView(this).apply {
            text = "暂无投递记录\n开启自动化寻岗后将在此实时汇总"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#757575"))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        root.addView(recyclerView)
        root.addView(emptyTv)
        setContentView(root)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@HistoryRecordActivity)
            val list = withContext(Dispatchers.IO) {
                db.jobDao().getRecentJobs(limit = 100)
            }

            records.clear()
            records.addAll(list)
            adapter.notifyDataSetChanged()

            emptyTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ==================== 极简高性能纯代码 Adapter ====================

    inner class HistoryAdapter(private val dataList: List<JobEntity>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTv: TextView = view.findViewWithTag("title")
            val salaryTv: TextView = view.findViewWithTag("salary")
            val companyTv: TextView = view.findViewWithTag("company")
            val statusChip: TextView = view.findViewWithTag("status")
            val scoreTv: TextView = view.findViewWithTag("score")
            val reasonTv: TextView = view.findViewWithTag("reason")
            val timeTv: TextView = view.findViewWithTag("time")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(10).toFloat()
                    setColor(Color.parseColor("#1C1C22"))
                    setStroke(1, Color.parseColor("#2E2E38"))
                }
                val p = dp2px(12)
                setPadding(p, p, p, p)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = dp2px(8)
                    setMargins(dp2px(12), m, dp2px(12), 0)
                }
            }

            // 第一行：标题 + 薪资
            val row1 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(parent.context).apply {
                tag = "title"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val salary = TextView(parent.context).apply {
                tag = "salary"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FFB74D"))
            }
            row1.addView(title)
            row1.addView(salary)
            card.addView(row1)

            // 第二行：公司 + 状态
            val row2 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(4) }
            }
            val comp = TextView(parent.context).apply {
                tag = "company"
                textSize = 12f
                setTextColor(Color.parseColor("#B0BEC5"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val status = TextView(parent.context).apply {
                tag = "status"
                textSize = 10f
                setTextColor(Color.WHITE)
                setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2))
            }
            row2.addView(comp)
            row2.addView(status)
            card.addView(row2)

            // 第三行：DeepSeek 打分与决策理由
            val reason = TextView(parent.context).apply {
                tag = "reason"
                textSize = 11f
                setTextColor(Color.parseColor("#9E9E9E"))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(6) }
            }
            card.addView(reason)

            // 第四行：底部时间与分数
            val row4 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(6) }
            }
            val time = TextView(parent.context).apply {
                tag = "time"
                textSize = 10f
                setTextColor(Color.parseColor("#616161"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val score = TextView(parent.context).apply {
                tag = "score"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#81C784"))
            }
            row4.addView(time)
            row4.addView(score)
            card.addView(row4)

            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = dataList[position]
            holder.titleTv.text = item.title
            holder.salaryTv.text = item.salaryText
            holder.companyTv.text = "${item.companyName} · ${item.city.ifBlank { "全国" }}"

            // 状态标签
            // 状态标签
            when (item.status) {
                // 🌟【新增/合并】：支持 STATUS_DELIVERED 显示高质感绿色标签
                JobEntity.STATUS_DELIVERED -> {
                    holder.statusChip.text = "已投递"
                    holder.statusChip.background = getChipDrawable("#2E7D32")
                }
                JobEntity.STATUS_COMMUNICATED -> {
                    holder.statusChip.text = "已打招呼"
                    holder.statusChip.background = getChipDrawable("#2E7D32")
                }
                JobEntity.STATUS_EVALUATED -> {
                    holder.statusChip.text = "评估通过"
                    holder.statusChip.background = getChipDrawable("#1565C0")
                }
                JobEntity.STATUS_FILTERED_OUT -> {
                    holder.statusChip.text = "本地过滤"
                    holder.statusChip.background = getChipDrawable("#616161")
                }
                JobEntity.STATUS_REJECTED -> {
                    holder.statusChip.text = "评估放弃"
                    holder.statusChip.background = getChipDrawable("#C62828")
                }
                else -> {
                    holder.statusChip.text = item.status
                    holder.statusChip.background = getChipDrawable("#424242")
                }
            }

            holder.reasonTv.text = item.evalReason.ifBlank { "尚未生成详细评价" }
            holder.scoreTv.text = if (item.matchScore >= 0) "${item.matchScore}分" else "--"

            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            holder.timeTv.text = sdf.format(Date(item.updatedAt))
        }

        override fun getItemCount(): Int = dataList.size

        private fun getChipDrawable(colorHex: String): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = dp2px(4).toFloat()
                setColor(Color.parseColor(colorHex))
            }
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
