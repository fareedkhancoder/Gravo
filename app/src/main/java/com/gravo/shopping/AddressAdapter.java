package com.gravo.shopping;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    private final List<Address> addressList;
    private final OnAddressActionsListener listener;
    private final MyAddressesActivity.AddressMode mode;
    private int selectedPosition = -1;

    public interface OnAddressActionsListener {
        void onAddressSelected(Address address);
        void onEditClicked(Address address);
        void onMakeDefault(Address address);
        void onDeleteClicked(Address address);
    }

    public AddressAdapter(List<Address> addressList, OnAddressActionsListener listener, MyAddressesActivity.AddressMode mode) {
        this.addressList = addressList;
        this.listener = listener;
        this.mode = mode;
    }

    @NonNull
    @Override
    public AddressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Updated to use the new item_address_card layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address_card, parent, false);
        return new AddressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        Address address = addressList.get(position);
        holder.bind(address, position);
    }

    @Override
    public int getItemCount() {
        return addressList != null ? addressList.size() : 0;
    }

    class AddressViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView ivAddressType;
        TextView tvName, tvSelectedBadge, tvAddressLine1, tvAddressLine2, tvPhone;
        ImageButton btnMore;

        public AddressViewHolder(@NonNull View itemView) {
            super(itemView);
            // Map IDs from item_address_card.xml
            cardView = (MaterialCardView) itemView;
            ivAddressType = itemView.findViewById(R.id.ivAddressType);
            tvName = itemView.findViewById(R.id.tvName);
            tvSelectedBadge = itemView.findViewById(R.id.tvSelectedBadge);
            tvAddressLine1 = itemView.findViewById(R.id.tvAddressLine1);
            tvAddressLine2 = itemView.findViewById(R.id.tvAddressLine2);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            btnMore = itemView.findViewById(R.id.btnMore);
        }

        public void bind(final Address address, int position) {
            if (address == null) return;

            // 1. Icon & Type Setup
            String type = address.getType() != null ? address.getType().toUpperCase() : "HOME";
            if ("WORK".equals(type)) {
                // Assuming you have an ic_work drawable. If not, replace with R.drawable.ic_home_outline
                ivAddressType.setImageResource(R.drawable.ic_work);
            } else {
                ivAddressType.setImageResource(R.drawable.ic_home_outline);
            }

            // 2. Name & Phone
            tvName.setText(address.getFullName() != null ? address.getFullName() : "");
            tvPhone.setText(address.getPhone() != null ? address.getPhone() : "");

            // 3. Address Lines Split
            String houseNo = address.getHouseNo() != null ? address.getHouseNo() + ", " : "";
            String street = address.getStreet() != null ? address.getStreet() : "";
            tvAddressLine1.setText(houseNo + street);

            String city = address.getCity() != null ? address.getCity() + ", " : "";
            String state = address.getState() != null ? address.getState() : "";
            String pincode = address.getPincode() != null ? " - " + address.getPincode() : "";
            tvAddressLine2.setText(city + state + pincode);

            // 4. Default Reset
            tvSelectedBadge.setVisibility(View.GONE);
            btnMore.setVisibility(View.GONE);
            cardView.setStrokeColor(Color.parseColor("#E5E7EB")); // Default border color
            cardView.setCardBackgroundColor(Color.WHITE);

            // 5. Mode Handling
            switch (mode) {
                case SELECT:
                    if (position == selectedPosition) {
                        tvSelectedBadge.setVisibility(View.VISIBLE);
                        tvSelectedBadge.setText("Selected");
                        // Optional: Highlight selected card slightly
                        cardView.setStrokeColor(Color.parseColor("#1D61E7"));
                        cardView.setCardBackgroundColor(Color.parseColor("#FAFAFA"));
                    }

                    itemView.setOnClickListener(v -> {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            selectedPosition = pos;
                            notifyDataSetChanged();
                            listener.onAddressSelected(address);
                        }
                    });
                    break;

                case DEFAULT_SETTER:
                    if (address.isDefault()) {
                        tvSelectedBadge.setVisibility(View.VISIBLE);
                        tvSelectedBadge.setText("Default");
                    }
                    itemView.setOnClickListener(v -> listener.onMakeDefault(address));
                    break;

                case NORMAL:
                default:
                    if (address.isDefault()) {
                        tvSelectedBadge.setVisibility(View.VISIBLE);
                        tvSelectedBadge.setText("Default");
                    }

                    btnMore.setVisibility(View.VISIBLE);

                    // Popup Menu for Edit & Delete inside the 3-dots button
                    btnMore.setOnClickListener(v -> {
                        PopupMenu popup = new PopupMenu(v.getContext(), btnMore);
                        popup.getMenu().add(Menu.NONE, 1, 1, "Edit");
                        popup.getMenu().add(Menu.NONE, 2, 2, "Delete");

                        popup.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == 1) {
                                listener.onEditClicked(address);
                                return true;
                            } else if (item.getItemId() == 2) {
                                listener.onDeleteClicked(address);
                                return true;
                            }
                            return false;
                        });
                        popup.show();
                    });
                    break;
            }
        }
    }
}