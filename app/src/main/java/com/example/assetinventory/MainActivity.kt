
package com.example.assetinventory

import android.app.Activity
import android.os.Bundle
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.text.InputType

class DBHelper(a: Activity) :
    SQLiteOpenHelper(a, "assets.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT UNIQUE,
                name TEXT,
                holder TEXT,
                location TEXT,
                qty INTEGER DEFAULT 0,
                status TEXT DEFAULT 'سالم'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT,
                kind TEXT,
                qty INTEGER,
                holder TEXT,
                location TEXT,
                date TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())
    }

    override fun onUpgrade(
        db: SQLiteDatabase, oldVersion: Int, newVersion: Int
    ) {}

    fun addItem(
        code: String, name: String,
        holder: String, location: String, qty: Int
    ): Boolean {
        val v = ContentValues().apply {
            put("code", code)
            put("name", name)
            put("holder", holder)
            put("location", location)
            put("qty", qty)
        }
        return writableDatabase.insert("items", null, v) != -1L
    }

    fun move(
        code: String, kind: String, amount: Int,
        holder: String, location: String
    ): String {
        if (amount <= 0) return "تعداد باید بیشتر از صفر باشد"

        val db = writableDatabase
        val c = db.rawQuery(
            "SELECT qty FROM items WHERE code=?",
            arrayOf(code)
        )

        if (!c.moveToFirst()) {
            c.close()
            return "کد کالا پیدا نشد"
        }

        val oldQty = c.getInt(0)
        c.close()

        if (kind == "خروج" && amount > oldQty) {
            return "موجودی کافی نیست"
        }

        val newQty = if (kind == "ورود")
            oldQty + amount else oldQty - amount

        val v = ContentValues().apply {
            put("qty", newQty)
            put("holder", holder)
            put("location", location)
        }
        db.update("items", v, "code=?", arrayOf(code))

        val m = ContentValues().apply {
            put("code", code)
            put("kind", kind)
            put("qty", amount)
            put("holder", holder)
            put("location", location)
        }
        db.insert("movements", null, m)

        return "ثبت $kind انجام شد؛ موجودی: $newQty"
    }

    fun report(): String {
        val c = readableDatabase.rawQuery(
            "SELECT code,name,qty,holder,location,status FROM items ORDER BY name",
            null
        )
        val s = StringBuilder()
        while (c.moveToNext()) {
            s.append("کد: ${c.getString(0)}\n")
            s.append("کالا: ${c.getString(1)}\n")
            s.append("موجودی: ${c.getInt(2)}\n")
            s.append("تحویل‌گیرنده: ${c.getString(3)}\n")
            s.append("محل: ${c.getString(4)}\n")
            s.append("وضعیت: ${c.getString(5)}\n")
            s.append("--------------------\n")
        }
        c.close()
        return if (s.isEmpty()) "هنوز کالایی ثبت نشده است"
        else s.toString()
    }
}

class MainActivity : Activity() {
    private lateinit var db: DBHelper
    private lateinit var code: EditText
    private lateinit var name: EditText
    private lateinit var holder: EditText
    private lateinit var location: EditText
    private lateinit var qty: EditText
    private lateinit var output: TextView

    private fun field(hintText: String, numeric: Boolean = false) =
        EditText(this).apply {
            hint = hintText
            textSize = 16f
            inputType = if (numeric)
                InputType.TYPE_CLASS_NUMBER
            else InputType.TYPE_CLASS_TEXT
            setPadding(16, 8, 16, 8)
        }

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DBHelper(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(TextView(this).apply {
            text = "مدیریت اموال و انبار"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        code = field("کد یا شماره پلاک")
        name = field("نام کالا")
        holder = field("نام تحویل‌گیرنده")
        location = field("محل استقرار یا پروژه")
        qty = field("تعداد", true)

        listOf(code, name, holder, location, qty).forEach {
            content.addView(it)
        }

        content.addView(button("ثبت کالای جدید") {
            val n = qty.text.toString().toIntOrNull() ?: 0
            if (code.text.isBlank() || name.text.isBlank() || n < 0) {
                msg("کد، نام و تعداد معتبر وارد کن")
            } else {
                val ok = db.addItem(
                    code.text.toString().trim(),
                    name.text.toString().trim(),
                    holder.text.toString(),
                    location.text.toString(),
                    n
                )
                msg(if (ok) "کالا ثبت شد" else "این کد قبلاً ثبت شده است")
                showReport()
            }
        })

        content.addView(button("ثبت ورود کالا") {
            move("ورود")
        })

        content.addView(button("ثبت خروج کالا") {
            move("خروج")
        })

        content.addView(button("نمایش موجودی همه کالاها") {
            showReport()
        })

        output = TextView(this).apply {
            textSize = 16f
            setPadding(8, 16, 8, 16)
            textDirection = TextView.TEXT_DIRECTION_RTL
        }
        content.addView(output)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        showReport()
    }

    private fun move(kind: String) {
        val amount = qty.text.toString().toIntOrNull() ?: 0
        val result = db.move(
            code.text.toString().trim(),
            kind,
            amount,
            holder.text.toString(),
            location.text.toString()
        )
        msg(result)
        showReport()
    }

    private fun showReport() {
        output.text = db.report()
    }

    private fun msg(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }
}
