package com.gravo.shopping;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class OrderStatusAdapter extends RecyclerView.Adapter<OrderStatusAdapter.StatusViewHolder> {

    private final List<OrderStatus> statusList;
    private Context context;

    public OrderStatusAdapter(List<OrderStatus> statusList) {
        this.statusList = statusList;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_order_status, parent, false);
        return new StatusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        OrderStatus orderStatus = statusList.get(position);
        holder.bind(orderStatus);
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    class StatusViewHolder extends RecyclerView.ViewHolder {
        ImageView ivStatusIcon;
        TextView tvStatusText;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            tvStatusText = itemView.findViewById(R.id.tvStatusText);
        }

        void bind(OrderStatus orderStatus) {
            tvStatusText.setText(orderStatus.getStatus());

            if (orderStatus.isCompleted()) {
                // Set styles for completed steps
                ivStatusIcon.setImageResource(R.drawable.ic_status_complete);
                tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.green)); // Make sure you have colorPrimary in colors.xml
            } else {
                // Set styles for pending steps
                ivStatusIcon.setImageResource(R.drawable.outline_cycle_24);
                tvStatusText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            }
        }
    }
}
