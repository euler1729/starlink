package com.example.starlink.adapters

import android.content.Context
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.starlink.activities.ChatActivity
import com.example.starlink.dataclass.DeviceInfo
import com.example.starlink.R

//This is the class of the recyclerView adapter.
// The click event is set here. When the user clicks on a device icon,
// the next activity is started from here.
class MessageAdapter(private val msgList: List<DeviceInfo>, private val context : Context):
    RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.usr_msg, parent, false) as CardView
        val holder = ViewHolder(cardView)
        cardView.setOnClickListener{
            val device = msgList[holder.adapterPosition]
            ChatActivity.startThisActivity(context, device.address, false, "")
        }
        return holder
    }

    override fun getItemCount(): Int =msgList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //Display the device name, if not set, display the device IP address
        holder.textView!!.text = msgList[position].let {
            if (msgList[position].name != "default") it.name
            else it.address
        }
        //Set the display of the number of unread messages
        holder.msgNum!!.apply {
            if (msgList[position].deviceCnt == 0)
                visibility = View.GONE
            else{
                visibility = View.VISIBLE
                text = msgList[position].deviceCnt.toString()
            }
        }
    }

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v){
        var msgNum: TextView? = null
        var textView: TextView? = null
        init {
            textView = v.findViewById(R.id.usr)
            msgNum = v.findViewById(R.id.msg_num)
        }
    }
}