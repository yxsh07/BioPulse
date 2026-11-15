package org.biopulse.android.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.biopulse.android.MainActivity;
import org.biopulse.android.R;
import org.biopulse.android.presentation.LoginViewModel;

public final class LoginFragment extends Fragment {
  public LoginFragment() {
    super(R.layout.fragment_login);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle saved) {
    LoginViewModel model = new ViewModelProvider(this).get(LoginViewModel.class);
    TextInputEditText email = view.findViewById(R.id.email),
        password = view.findViewById(R.id.password),
        server = view.findViewById(R.id.server_address);
    email.setText(model.email());
    server.setText(model.server());
    password.setSaveEnabled(false);
    MaterialButton signIn = view.findViewById(R.id.sign_in);
    Runnable submit =
        () ->
            model.login(
                String.valueOf(server.getText()),
                String.valueOf(email.getText()),
                String.valueOf(password.getText()));
    signIn.setOnClickListener(v -> submit.run());
    password.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_DONE) {
            submit.run();
            return true;
          }
          return false;
        });
    view.findViewById(R.id.settings_toggle)
        .setOnClickListener(
            v -> {
              View settings = view.findViewById(R.id.connection_settings);
              settings.setVisibility(
                  settings.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            });
    model
        .message()
        .observe(
            getViewLifecycleOwner(),
            message -> ((TextView) view.findViewById(R.id.login_error)).setText(message));
    model
        .busy()
        .observe(
            getViewLifecycleOwner(),
            busy -> {
              signIn.setEnabled(!busy);
              view.findViewById(R.id.login_progress).setVisibility(busy ? View.VISIBLE : View.GONE);
            });
    model
        .signedIn()
        .observe(
            getViewLifecycleOwner(),
            authenticated -> {
              if (authenticated) {
                password.setText("");
                ((MainActivity) requireActivity()).showWorkspace();
              }
            });
  }
}
