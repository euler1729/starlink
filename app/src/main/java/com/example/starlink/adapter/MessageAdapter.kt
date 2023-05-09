package com.example.starlink.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.starlink.ChatActivity
import com.example.starlink.dataClass.DeviceInfo
import com.example.starlink.R

//This is the class of the recyclerView adapter.
//The click event is set here. When the user clicks on a device icon,
//the next activity is started from here.
class MessageAdapter(private val msgList: List<DeviceInfo>, private val context: Context) :
    RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var txtView: TextView? = null
        var msgNum: TextView? = null

        init {
            txtView = view.findViewById(R.id.user)
            msgNum = view.findViewById(R.id.msg_num)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_message, parent, false) as CardView
        val holder = ViewHolder(cardView)
        cardView.setOnClickListener {
            val device = msgList[holder.adapterPosition]
            ChatActivity.startThisActivity(context, device.address, false, "")
        }
        return holder
    }

    override fun getItemCount(): Int {
        return msgList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //Display the device name, if not set, display the device IP address
        holder.txtView!!.text = msgList[position].let {
            if (msgList[position].name != "default") it.name
            else it.address
        }
        //Set the display of the number of unread messages
        holder.msgNum!!.apply {
            if (msgList[position].new == 0)
                visibility = View.GONE
            else {
                visibility = View.VISIBLE
                text = msgList[position].new.toString()
            }
        }
    }
}