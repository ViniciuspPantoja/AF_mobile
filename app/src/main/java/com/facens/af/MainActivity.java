package com.facens.af;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int CODIGO_PERMISSAO_GPS = 42;

    // Firestore
    private FirebaseFirestore firestore;
    private CollectionReference colecaoRegistros;

    // Campos do formulário
    private EditText campoNome;
    private EditText campoNota;
    private EditText campoDataVisita;
    private EditText campoTipo;
    private CheckBox checkDestacado;

    // Informações de localização e clima
    private TextView infoLat;
    private TextView infoLng;
    private TextView infoTemp;
    private TextView infoCondicao;

    // Botões
    private Button btnCapturarGPS;
    private Button btnVerMapa;
    private Button btnRegistrar;

    // GPS
    private FusedLocationProviderClient clienteGPS;

    private double coordLat = 0.0;
    private double coordLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inicializarFirestore();
        vincularViews();
        configurarBotoes();

        clienteGPS = LocationServices.getFusedLocationProviderClient(this);
    }

    private void inicializarFirestore() {
        firestore = FirebaseFirestore.getInstance();
        colecaoRegistros = firestore.collection("registros");
    }

    private void vincularViews() {
        campoNome       = findViewById(R.id.etTitulo);
        campoNota       = findViewById(R.id.etDescricao);
        campoDataVisita = findViewById(R.id.etData);
        campoTipo       = findViewById(R.id.etCategoria);

        checkDestacado  = findViewById(R.id.cbFavorito);

        infoLat       = findViewById(R.id.tvLatitude);
        infoLng       = findViewById(R.id.tvLongitude);
        infoTemp      = findViewById(R.id.tvTemperatura);
        infoCondicao  = findViewById(R.id.tvClima);

        btnCapturarGPS = findViewById(R.id.btnLocalizacao);
        btnVerMapa     = findViewById(R.id.btnMapa);
        btnRegistrar   = findViewById(R.id.btnSalvar);
    }

    private void configurarBotoes() {
        btnCapturarGPS.setOnClickListener(v -> buscarPosicaoAtual());
        btnRegistrar.setOnClickListener(v -> gravarRegistro());
        btnVerMapa.setOnClickListener(v -> navegarParaMapa());
    }

    private void buscarPosicaoAtual() {
        boolean semPermissao = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED;

        if (semPermissao) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    CODIGO_PERMISSAO_GPS
            );
            return;
        }

        clienteGPS.getLastLocation().addOnSuccessListener(posicao -> {
            if (posicao != null) {
                coordLat = posicao.getLatitude();
                coordLng = posicao.getLongitude();
                infoLat.setText("Lat: " + coordLat);
                infoLng.setText("Lng: " + coordLng);
            } else {
                Toast.makeText(this, "Não foi possível obter a posição", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int codigo,
                                           @NonNull String[] permissoes,
                                           @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigo, permissoes, resultados);

        if (codigo == CODIGO_PERMISSAO_GPS) {
            boolean concedida = resultados.length > 0
                    && resultados[0] == PackageManager.PERMISSION_GRANTED;

            if (concedida) {
                buscarPosicaoAtual();
            } else {
                Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void gravarRegistro() {
        String nome      = campoNome.getText().toString().trim();
        String nota      = campoNota.getText().toString().trim();
        String data      = campoDataVisita.getText().toString().trim();
        String tipo      = campoTipo.getText().toString().trim();
        boolean destaque = checkDestacado.isChecked();

        if (nome.isEmpty()) {
            campoNome.setError("Informe um nome para o destino");
            return;
        }

        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nome);
        dados.put("nota", nota);
        dados.put("data", data);
        dados.put("tipo", tipo);
        dados.put("lat", coordLat);
        dados.put("lng", coordLng);
        dados.put("temperatura", infoTemp.getText().toString());
        dados.put("condicao", infoCondicao.getText().toString());
        dados.put("destaque", destaque);

        colecaoRegistros.add(dados)
                .addOnSuccessListener(ref ->
                        Toast.makeText(this, "Destino registrado!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Falha ao registrar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void navegarParaMapa() {
        if (coordLat == 0.0 && coordLng == 0.0) {
            Toast.makeText(this, "Capture a localização antes de abrir o mapa", Toast.LENGTH_SHORT).show();
            return;
        }

        String geoUri = "geo:" + coordLat + "," + coordLng
                + "?q=" + coordLat + "," + coordLng;

        Intent intencaoMapa = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
        intencaoMapa.setPackage("com.google.android.apps.maps");

        if (intencaoMapa.resolveActivity(getPackageManager()) != null) {
            startActivity(intencaoMapa);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + coordLat + "," + coordLng)));
        }
    }
}
