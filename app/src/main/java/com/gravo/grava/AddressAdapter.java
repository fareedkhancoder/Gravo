package com.gravo.grava;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    private final List<Address> addressList;
    private final OnAddressActionsListener listener;
    private final MyAddressesActivity.AddressMode mode;  // ✅ use enum
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address, parent, false);
        return new AddressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        Address address = addressList.get(position);
        holder.bind(address, position);
    }

    @Override
    public int getItemCount() {
        return addressList.size();
    }

    class AddressViewHolder extends RecyclerView.ViewHolder {
        TextView addressTypeTextView, userNameAddressTextView, fullAddressTextView, phoneTextView, defaultTagTextView;
        ImageView editButton, deleteButton;
        RadioButton selectionRadioButton;
        View actionButtonsLayout;

        public AddressViewHolder(@NonNull View itemView) {
            super(itemView);
            addressTypeTextView = itemView.findViewById(R.id.addressTypeTextView);
            userNameAddressTextView = itemView.findViewById(R.id.userNameAddressTextView);
            fullAddressTextView = itemView.findViewById(R.id.fullAddressTextView);
            phoneTextView = itemView.findViewById(R.id.phoneTextView);
            editButton = itemView.findViewById(R.id.editAddressButton);
            deleteButton = itemView.findViewById(R.id.deleteAddressButton);
            selectionRadioButton = itemView.findViewById(R.id.selectionRadioButton);
            actionButtonsLayout = itemView.findViewById(R.id.actionButtonsLayout);
            defaultTagTextView = itemView.findViewById(R.id.defaultTagTextView);
        }

        public void bind(final Address address, int position) {
            // Basic data
            addressTypeTextView.setText(address.getType().toUpperCase());
            userNameAddressTextView.setText(address.getFullName());
            String fullAddressStr = address.getStreet() + ", " + address.getCity() + ", " + address.getState() + " - " + address.getPincode();
            fullAddressTextView.setText(fullAddressStr);
            phoneTextView.setText("Phone: " + address.getPhone());

            // Default tag
            if (address.isDefault()) {
                defaultTagTextView.setVisibility(View.VISIBLE);
                defaultTagTextView.setText("Default");
            } else {
                defaultTagTextView.setVisibility(View.GONE);
            }

            // Mode handling
            switch (mode) {
                case SELECT:
                    selectionRadioButton.setVisibility(View.VISIBLE);
                    selectionRadioButton.setChecked(position == selectedPosition);
                    actionButtonsLayout.setVisibility(View.GONE);

                    itemView.setOnClickListener(v -> {
                        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                            selectedPosition = getAdapterPosition();
                            notifyDataSetChanged();
                            listener.onAddressSelected(address);
                        }
                    });
                    break;

                case DEFAULT_SETTER:
                    selectionRadioButton.setVisibility(View.GONE);
                    actionButtonsLayout.setVisibility(View.GONE);

                    itemView.setOnClickListener(v -> listener.onMakeDefault(address));
                    break;

                case NORMAL:
                default:
                    selectionRadioButton.setVisibility(View.GONE);
                    actionButtonsLayout.setVisibility(View.VISIBLE);

                    editButton.setOnClickListener(v -> listener.onEditClicked(address));
                    deleteButton.setOnClickListener(v -> listener.onDeleteClicked(address));
                    break;
            }
        }
    }
}
