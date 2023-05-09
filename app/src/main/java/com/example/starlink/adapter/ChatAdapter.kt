package com.example.starlink.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.starlink.dataClass.ChatMessage
import com.example.starlink.R

class ChatAdapter(private val msgList: List<ChatMessage>)
    : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return msgList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = msgList[position]
        when (msg.type) {
            ChatMessage.TYPE_RECEIVED -> {
                holder.leftLayout!!.visibility = View.VISIBLE
                holder.rightLayout!!.visibility = View.GONE
                holder.wrongLayout!!.visibility = View.GONE
                holder.leftMsg!!.text = msg.content
            }
            ChatMessage.TYPE_SEND -> {
                holder.rightLayout!!.visibility = View.VISIBLE
                holder.leftLayout!!.visibility = View.GONE
                holder.wrongLayout!!.visibility = View.GONE
                holder.rightMsg!!.text = msg.content
            }
            ChatMessage.TYPE_WRONG -> {
                holder.wrongLayout!!.visibility = View.VISIBLE
                holder.leftLayout!!.visibility = View.GONE
                holder.rightLayout!!.visibility = View.GONE
                holder.wrongMsg!!.text = msg.content
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var leftLayout: LinearLayout? = null
        var rightLayout: LinearLayout? = null
        var wrongLayout: LinearLayout? = null
        var leftMsg: TextView? = null
        var rightMsg: TextView? = null
        var wrongMsg: TextView? = null

        init {
            leftLayout = view.findViewById(R.id.left_layout)
            rightLayout = view.findViewById(R.id.right_layout)
            wrongLayout = view.findViewById(R.id.wrong_layout)
            leftMsg = view.findViewById(R.id.left_msg)
            rightMsg = view.findViewById(R.id.right_msg)
            wrongMsg = view.findViewById(R.id.wrong_msg)
        }
    }
}