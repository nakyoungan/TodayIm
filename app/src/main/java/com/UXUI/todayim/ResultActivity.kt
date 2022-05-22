package com.UXUI.todayim

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.UXUI.todayim.base.BaseActivity
import com.UXUI.todayim.base.TEST_REPEAT_NUM
import com.UXUI.todayim.database.*
import com.UXUI.todayim.databinding.ActivityResultBinding
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


/**
 * 결과 화면
 */
class ResultActivity: BaseActivity() {
    private var mBinding: ActivityResultBinding? = null
    private val binding get() = mBinding!!

    private lateinit var roomDatabase: DiaryDatabase
    private val gson = Gson()

    private val progressBarInterval = 100/TEST_REPEAT_NUM

    private lateinit var adjectiveResult: List<DiaryEmotionDetail>
    private lateinit var adjectiveNum: List<Int>
    private lateinit var categoryResult: List<DiaryEmotionCategory>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setInitialize()

        if(intent.getStringExtra("categoryResult").isNullOrEmpty()) {
            getDiaryFromDB()
        } else {
            getDataFromTest()
        }

        setResultProgressbar()
    }

    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }

    private fun setInitialize() {
        // 뷰 바인딩 구성
        mBinding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // room data base init
        roomDatabase = DiaryDatabase.getInstance(applicationContext)!!

        binding.btnComplete.setOnClickListener(this)
    }

    private fun setResultProgressbar() {
        var i = 0

        while (i < categoryResult.size) {
            val layoutInflater = this.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val categoryProgress = layoutInflater.inflate(R.layout.layout_result_progressbar, null)

            categoryProgress.findViewById<TextView>(R.id.layout_result_category_tv).text =
                categoryResult[i].adjectiveCategoryName

            categoryProgress.findViewById<ProgressBar>(R.id.layout_result_progressbar).progress = adjectiveNum[i] * (progressBarInterval.toInt())

            binding.resultProgressBarLayout.addView(categoryProgress)

            i++
        }
    }

    private fun getDiaryFromDB() {

        if( intent.getStringExtra("diaryInfo").isNullOrEmpty() ) return

        val nowDiary: Diary = gson.fromJson(
            intent.getStringExtra("diaryInfo"),
            Diary::class.java
        )

        binding.resultContentEt.setText(nowDiary.diaryComment)

        val diaryIdx = nowDiary.diaryIdx
        categoryResult = roomDatabase.diaryDao().getDiaryCategories(diaryIdx)
        adjectiveResult = roomDatabase.diaryDao().getDiaryAdjectives(diaryIdx)
        adjectiveNum = countAdjectiveResult(adjectiveResult)
    }

    private fun getDataFromTest() {
        val rawAdjectiveResult = gson.fromJson(
            intent.getStringExtra("adjectiveResult"),
            Array<DiaryEmotionDetail>::class.java
        )
        Log.d("rawAdjectiveResult", rawAdjectiveResult.toString())

        val rawCategoryResult = gson.fromJson(
            intent.getStringExtra("categoryResult"),
            Array<DiaryEmotionCategory>::class.java
        )
        Log.d("rawCategoryResult", rawCategoryResult.toString())

        val comparator1: Comparator<DiaryEmotionDetail> = compareBy { it.adjectiveCategoryIdx }
        adjectiveResult = rawAdjectiveResult.sortedWith(comparator1)
        Log.d("ResultActivity", "adjectiveResult : $adjectiveResult")

        adjectiveNum = countAdjectiveResult(adjectiveResult)
        Log.d("adjectiveNum", "adjectiveResult : $adjectiveNum")

        val comparator2: Comparator<DiaryEmotionCategory> = compareBy { it.adjectiveCategoryIdx }
        categoryResult = rawCategoryResult.sortedWith(comparator2)
        Log.d("ResultActivity", "categoryResult : $categoryResult")
    }

    private fun countAdjectiveResult(adjectiveResult: List<DiaryEmotionDetail>): ArrayList<Int>{
        val adjectiveNum = ArrayList<Int>()

        var i = 0
        var count = 0
        var nowCategory: Int = -1
        while( i < TEST_REPEAT_NUM ) {
            if(nowCategory != adjectiveResult[i].adjectiveCategoryIdx) {
                if (nowCategory != -1) {
                    adjectiveNum.add(count)
                }
                count = 1
                nowCategory = adjectiveResult[i].adjectiveCategoryIdx
            } else {
                count++
            }
            i++
        }
        adjectiveNum.add(count)
        return adjectiveNum
    }

    @SuppressLint("SimpleDateFormat")
    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.btn_complete-> { // 완료 버튼 클릭
                // 유효성 검사 (빈 입력필드)
//                if (binding.resultContentEt.text.isEmpty()) {
//                    Toast.makeText(binding.root.context, "입력 값이 비어있습니다", Toast.LENGTH_SHORT).show()
//                    return
//                }

                // 일기 데이터 생성
                var diaryIdx: Int =0
                val diaryDate = SimpleDateFormat("yyyy-MM-dd").format(Date())

                val diaryInfo = if (binding.resultContentEt.text.isEmpty()) {
                        Diary( diaryDate = diaryDate )
                    } else {
                        Diary(
                            diaryComment = binding.resultContentEt.text.toString(),
                            diaryDate = diaryDate
                        )
                }

                roomDatabase.diaryDao().insertDiaryData(diaryInfo)
                diaryIdx = roomDatabase.diaryDao().getDiaryIdx(diaryDate)

                var i = 0
                while( i < categoryResult.size ) {
                    categoryResult[i].diaryIdx = diaryIdx
                    // 코루틴을 활용하여 비동기 스레드 에서 DB Insert
                    CoroutineScope(Dispatchers.IO).launch {
                        roomDatabase.diaryDao().insertDiaryEmotionCategory(categoryResult[i])
                    }
                    i++
                }
                i=0
                Log.d("ResultActivity>>>", "adjectiveResult : $adjectiveResult")
                while( i < adjectiveResult.size ) {
                    adjectiveResult[i].diaryIdx = diaryIdx

                    if(i>= adjectiveResult.size) break
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.d("ResultActivity>>>", "adjectiveResult[$i] : ${adjectiveResult[i]}")
                        roomDatabase.diaryDao().insertDiaryEmotionDetail(adjectiveResult[i])
                    }
                    i++
                }

                // Toast 메시지는 UI 처리이므로 UI 쓰레드에서 별도로 돌림.
                runOnUiThread { Toast.makeText(binding.root.context, "일기 작성이 완료 되었습니다", Toast.LENGTH_SHORT).show() }
                // 액티비티 종료
                finish()
            }
        }
    }
}