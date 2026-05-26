package com.answufeng.net.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DemoHomeAdapter(
    private val onEntryClick: (DemoListItem.Entry) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = DemoCatalog.items()

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is DemoListItem.Section -> VIEW_SECTION
            is DemoListItem.Entry -> VIEW_ENTRY
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SECTION -> {
                val view = inflater.inflate(R.layout.item_demo_section, parent, false)
                SectionViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_demo_entry, parent, false)
                EntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = items[position]) {
            is DemoListItem.Section -> (holder as SectionViewHolder).bind(item)
            is DemoListItem.Entry -> (holder as EntryViewHolder).bind(item, onEntryClick)
        }
    }

    override fun getItemCount(): Int = items.size

    private class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvSectionTitle)

        fun bind(item: DemoListItem.Section) {
            title.text = item.title
        }
    }

    private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)

        fun bind(
            item: DemoListItem.Entry,
            onClick: (DemoListItem.Entry) -> Unit,
        ) {
            icon.setImageResource(item.iconRes)
            title.text = item.title
            subtitle.text = item.subtitle
            itemView.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private const val VIEW_SECTION = 0
        private const val VIEW_ENTRY = 1
    }
}
