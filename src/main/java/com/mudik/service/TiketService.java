package com.mudik.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@ApplicationScoped
public class TiketService {

    // ── Warna ──────────────────────────────────────────────────────────────
    private static final Color BLUE_PRIMARY    = new Color(0x25, 0x63, 0xEB);
    private static final Color BLUE_LABEL      = new Color(0x29, 0x78, 0xFF);
    private static final Color BLUE_LIGHT_TEXT = new Color(0x93, 0xC5, 0xFD);
    private static final Color WHITE           = Color.WHITE;
    private static final Color SLATE_900       = new Color(0x0F, 0x17, 0x2A);
    private static final Color SLATE_500       = new Color(0x64, 0x74, 0x8B);
    private static final Color SLATE_200       = new Color(0xE2, 0xE8, 0xF0);
    private static final Color BG_DARK         = new Color(0x05, 0x0D, 0x1F);

    private static final String LOGO_DISHUB_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAXwAAABtCAYAAACiGtpAAAABCGlDQ1BJQ0MgUHJvZmlsZQAAeJxjYGA8wQAELAYMDLl5JUVB7k4KEZFRCuwPGBiBEAwS" +
                    "k4sLGHADoKpv1yBqL+viUYcLcKakFicD6Q9ArFIEtBxopAiQLZIOYWuA2EkQtg2IXV5SUAJkB4DYRSFBzkB2CpCtkY7ETkJiJxcUgdT3ANk2uTmlyQh3" +
                    "M/Ck5oUGA2kOIJZhKGYIYnBncAL5H6IkfxEDg8VXBgbmCQixpJkMDNtbGRgkbiHEVBYwMPC3MDBsO48QQ4RJQWJRIliIBYiZ0tIYGD4tZ2DgjWRgEL7A" +
                    "wMAVDQsIHG5TALvNnSEfCNMZchhSgSKeDHkMyQx6QJYRgwGDIYMZAKbWPz9HbOBQAACVAUlEQVR42uydd7hcVdX/P3ufMu32lt5IJ5WEhI6A9P4apBdB" +
                    "UECQqhRBBFtQVERQEJHeiy8dNBQDkYSSkAZppPfc3qacc/b+/XHmnMzckg7B93f388xzb25mZu/Tvmvt71rruwSg+RoNmV2QFv4vApH9DwFIpHLRgNGv" +
                    "FOuogWCC8+ZSvC9qAdBSItCg/MPSAvTX6gi7RtfoGl1j9wzxdQP8cFWA0D7QCw1CaxQg+5YS+eYepMYUo2wPJAjXJjGrnsxbS8gsr/M/KwyE0Ait8boQ" +
                    "v2t0ja7RNb6GgC+Cn/4vMuupy0ElWN/sR3pkJcrIUGUXc9aex1GgJQ/Me5G1XgtS2cTm1JJ5eynuEt/jx5Bopbvc/K7RNbpGF+B//QA/S+JojQbMIWXE" +
                    "Dt2DltFVKJGih1XCWcOP5OLhxzOwrBcAK+rW87fPX+Ghz95gtVODxKZgTgOpt5eSWbRpswERhFRPrm3pMgVdo2t0jS7A/wo8eaFBC4EUILTA0woJyIFl" +
                    "GEcMIDOmGI1HT1nFmUMP4+KxxzOwuDcAnueihMCSBgBfNK/jwU9f4cHPXmetqsf0bCLzqsm8sxR3YR0aEKZEaxBKA7qtDegaXaNrdI0uwP+S8B4tBYYG" +
                    "nfXorcGVGEcMJDm6AHSGHmYZFw4/hgtHnUDfoh6gwVUuQgikkH6AFwWeh2HaAKxqqubhea9y92cvssGtQ+g4sc9rcd5chLuw1t85CIGW+Py+6roRukbX" +
                    "6BpdgP+lzCg0SCl8nM2619bQUsThe5DesxgMh15mOecOPZ6LRp/AgKIqAFLKxRQCA7n567QGFI6UoAXS8zBMAxAsa17PI7Pf4MHPXmGF3ohwIkQ/b8Z5" +
                    "azF6QU1ocHLX0TW6RtfoGl2Av6uGIRDK9+iFENgjyjAOGUjriHIgxYBIBecNOYZLxpxIVUE5kKVupEAiMJQP0h0NDSAUKI2rJbbhG4Z1rdU89Okb3L/w" +
                    "DZal14AwiSxsQv9zGc78jdnPZTl+rfPOyNczjalrdI2u0TW+ToDfhqM3hQStcLXPm1vDqpBH9ic1sgCUSx+rF98dfASXjD+JqngZAK7nIaRGIhCILOPe" +
                    "yXR687w6e1hKKzw0tjQBWJ2u4+8zX+bhz19maWYTQsSIf16PN+UL0vNrAAWGCJP3NRrZ5fx3ja7RNboAf5vwPsyjD7Ju7FE9MQ7rT+vwBHgtDIj15/vD" +
                    "juXcMUfQI17lc/TaQwBSyF2yFj8+62EICVKyrrWBR+e+xn0LXmFp60owbOKfN6LeWkV67gZ0dm4hNErrrozOrtE1ukYX4Hf2jUKDFAIP7Xv4gDWqEvOw" +
                    "PUiOKAavlT2iPTh/2IlcMOY4esZKQUNGKaQQGHmOvMouU2zHInIOSWvAwJXaz8xRCtP0Pf51rbU8Mm8KD8x7lcXOCsAk8XkzzjsrcOau9w2VACVEl5vf" +
                    "NbpG1+gC/HZDCoTyvXkhJfbIbhhHDKB1sE/dDIn35Lsjj+U7w4/aTN24HsoQGJDP0WsNMuvla8Vm4YWtgb0I8/kVoLVCaokSgNAI7eF5Asv00zk3ttbz" +
                    "+Of/5P75L/F562oQFtEvWvGmLMf9dK1vMzrI4+8aXaNrdI3/84BvZH96sLkiVmQxOcugG3t1xzx8AMmBcVAZ9oj24vLRk/jOiKMoiRYCGtfLIJFIYWTB" +
                    "OIP0bDwJ4GJoG1ctAuKYsjdKOPhmYbO3LzQoodAYGNpD4yGkhVt/Pyr5H8yKG1DWUAyVQugInswgkChMpHZJo4jJCAC1mSYem/sGf57zPAszG0BGSCxp" +
                    "xfvnEtKzNyK0QguNkH71rkYju7I6u0bX6Br/ZwFftPlVCqTn69wIy8Aa1RPj8AG0DrRAJdkzMZCLR5zIGcOOoCJeDDh4nkAIiZDgaYkQaaQXQcgkSphI" +
                    "5YKM4Tb/Hb3mWkxsVP9HkZEjQHmQw+1rNGiJEA5KKCCCrr0N1t/i2yJ7ALL3/+JERmM5Lp7hc/OGBxgG4KA1uFpiGgYCqEu18Nhnb3Df/JeZ37wcDEnh" +
                    "4lYyb68gM2cD2tV+GFn68+suxO8aXaNr/F8EfAM/iUUJgQypG4E9pgp51CBa94iDm2REvA/fH/Etzhl7FCVmISjIaIUhNYaWeFKj00uQRgXCKEUoF7SJ" +
                    "JxVIA5qexVt3PmZ0Ap67DkUSq+8HCKMnuby+EhmkZyLwUIaFaHmB9JrziZTfhE4ciFv9U0ivxOrzHI49CstLARLPsBHOIrzGd7HLzscVJlKBViqbxw+1" +
                    "ThOPznuLv81+kXnpL8CwiC1OoqesIDN7Ldrzdzi6i+rpGl2ja/yfA3zhg7v0NB4gbAN7bBXGoQNpHZgAN8noogFcNOJkzhl+OMWRgrAyVhkKQ5ugNVJr" +
                    "MEycphdwkv8iUXEHHhGENP00yLp7yGz4EVblzYjyHyN0De7aH6A0RHo/jtZmuNVQQmN4GiUNhLOS1JpvY5f/CIpOyVI+dThrTka2LMHs8xdE/EQygKlq" +
                    "yaw+E1F0PHbJZeB5CCHxhIcGlJLYQoKEJqeFx+ZN4d75LzKn8Qswo8SWJlFvLcP5dA3KzZ5CKdrl8XeNrtE1usZ/DeALJFpopBCgJAoXTEF0fG+Mbw6g" +
                    "pb8EpRlZMJSrRv8P3x52MIVWHICM8jCRSDRaKLTI6tigEBgI3UzrqmOJRPdGdfsDlqpBrb8Fp/FvmN1uRJb+FE8rDCQCh9TasxCx8UTKrkNrB7BQwvWD" +
                    "xNLAW30pomAEZsnlaJVGAq6MIFUjbLiMTOMLmEXnIMuvQlf/FkfNI9rrDaAAX6DBQAmFqXzJBiU0nvawhQlC0OQmeWH+v7lz3hN82rQSZJTE0hTq3aUk" +
                    "Z65GONm8fcPP6ulK58y5j4TIe+nsydFah7/nvi/4Pfc9SnVxZ12ja3y5Hr7wiRxDexABe68+iMP2oLW/BV6GsUXDuHT0CZwx9BAKrMCj95BSbG5ekvd1" +
                    "Gi0clFYIEUPW/YXMpisxK67GafwUT2zErrgRIzEJT3m+Zo7WPm/vbqCx5koKSn6AjOyfzcdxgChu03O46X8Tq/xjFkAkIBDaRQmJJwSi5VVoeATcxYjW" +
                    "eaiqXyLLfuzHDPwKMf9zYvO6w2YsrsIwDBDQ4iR5asFb3DP3H8xq/AIMg/iyFtQ7q8jM2oBIexhIHJHV+fn/FPillEgp0Vrjed4u+U7TNEPw110WtWt0" +
                    "jV0L+KYE15BE9uoFRw8g3ccA5TAuMYgrx57OpKGHETetHI4eZIc58z7waQQ6G3SVArzGvyDWX4rUZagef8YoOh5NAqU1Ag+JC9rGFR5CWIjkTFIt/yZW" +
                    "cQVaewhtoUQLasPfMMpPA6s7QivyosvaRWkDYfgry6w9FrvhTXR8Aqrbr5DR/RFE/RROwFCe/5vwjYYWILMZSK5WYJiYQIvXyrOfT+VPnz7PzObFICXx" +
                    "lQ7qX8tIz1yH9kBqlRV4+/8L5D3PywPkRCLBHnvswZAhQxg6dCi9e/emZ8+elJSUEIlE8jz+5uZmqqurWbVqFatWrWLBggUsXLiQ1atXd4F/1+gaXxrg" +
                    "C4GpBbJ3DPOmw0hbDhMj/bl47Il8e/AhxOwYnlYopfwHXWcLpEKdA1/YzPeaczJrvAZ0eia0PodueA5TbyQjS6H/x9hiD7R20ZKwz6FEobWFEp5PmahG" +
                    "pCgEYfhUkXKQOomSZUitsh66DsEaLfygsNRo7cLKb6HTbyA1eGYUHdkfGT8S4iOQ9gQwuuUYC98zDYxUINamPIEUBsLwPf7nF77Hn+b8g1mZZSRaDVp/" +
                    "/jbu+lQeffF/HeiFEHme/MiRIznssMP4xje+wdixY+nbt29Y9La9o6GhgYULF/Lxxx/z1ltv8d5777Fp06bw/w3D6AL+rtE1dhbw0RAZUU7m0hEcXDWW" +
                    "N//nt0QMKyuB4GAIjRIm6CxXj++ZozUCG2SW1nCXoZLvQfO/UemPwJ2P4fgr8KSNKDoVo/IvaBHxY59IFBKhBYEIMkKhkGgkUuMLpaHR2kBLkFpnAdnX" +
                    "yQeJ0A5KWH6ePhmUkJBZimr8M7L5HcgsA68JlZXQMazBEN8fEt9EJA5EyAEIBY5wfU0gNtszjUBphZR+lCGlMhz67BVMr16IfdenZBbVIQwJnvo/6+Eb" +
                    "hp/RFAB9//79OeWUU5g0aRLjx4/Hsqy897e0tLB+/Xo2btxITU0NdXV1tLS0hN69ZVkUFRVRXl5ORUUF3bp1o7KyMpwnGGvWrOHtt9/mqaeeYsqUKWQy" +
                    "mdDrb7u76Bpdo2tkd8VbtwgaXRxHC82wsv5EDIuM62AaBoYw0YGsmcj4PrX2QMd8SQK9DK/pHXTzm4iWqcjMerQAYRZCZCKqYAAqMhgZPQgjehhgInUr" +
                    "KRHDypZYeToJwkRhoPH71G724v1X0LvWT9OUIOysQQCEgQEo0mhtIpSHNIdgVNyJLq1DuQtRzgpEZhki8zEqNQtR9zCi5mFUtBe6+CRE8elY1kE+JRXO" +
                    "7X+9iYGnwfEcoqZN/6LuTK9ZhC6MA3VhOOD/GvwIITAMA9d1Adh///259NJLOf744ykuLg7fV1NTw5w5c5g1axbz5s1j6dKlbNq0iebmZjzPw3XdPDon" +
                    "oIQMwyASiVBaWkqPHj0YOnQo48aNY9y4cQwZMoRevXpxzjnncM455zBv3jwefPBBHn300dDrNwxjl8UNukbX+P/CwxfSzzaxjxpM+n96cMuoC7hl/3Px" +
                    "PIWRlTzIEie+R68FypAItQGv7hF0/d1odyVS2MjY/uiCb6Kj45DWAITsBbIoXICnFIaQfo6/dhGNz+K4K5AFJ2Fbg1AIEGaWMcpdcoDsZGUT0njphSAa" +
                    "kUqSTs/AsIZhxo/BQWKJLNGkNQbar7oNlJHx0GoTIvMFOjUV3fgCovljlIxCycWYFT8AcxBauQhMfz4UHtLP3zckl7/9R+5Z9DLWU8tx/r0UIYXfU/f/" +
                    "kpdgmiHQT5w4kRtuuIGTTjopzKppbW1l2rRpvP7663zwwQesWbOGTCaDYRjYto1pmhiGkZe1E17NHPBXSuF5Ho7jhB58cXExw4YN49BDD+WYY45hyJAh" +
                    "4WdXr17NH//4R+69916am5u7aJ6u0TW2C/CFn1dun7In6cMruG//H/G9UcfjqqzyJKCFQigDtEIYAtXyLu6maxGpmRAdhFF0NsROhuhwlLD99oVBT3Gt" +
                    "0MIBIZFYeOnPgBRG9S9xW2Yj+j2EjB64uTuWWw/SBFGQs+xsgFYDUqKaX8StfxEvOgSZfBkzvQwlYsjSq6H0LETrLLS7AQq+iTBKQXtIJdBSojD8Qir8" +
                    "HB+pmlHJd/EaH0Q2voA2+yC63Y1RcHxWR8JEoFAIPK2xpOS26Q9xy5zHsF5eg/PKgv9TgB+As1KKnj17cvPNN3PBBRdg236nsVQqxTPPPMOdd97JihUr" +
                    "iEQiFBQUhLRObmrltoJw23ROpRSpVIrW1lYsy2L//ffnJz/5CcOHDw/XsWDBAm655RaeeeaZLm+/a3SN7aF0ACi1wYjQt7Cy/QOpsyI6Brh1f4ENVyDM" +
                    "YoxudyIKzwezKNSaMZWT5eZB4uJJgSKCFK24m24ho9cRSa/Da3oTr+et2NEJqOpfgtiEcjMoayJWycn+jiL07LPBYKEQgKNdZGIisdIz8Fa9jKmawKgj" +
                    "XXc9ovFPiGxcgJZ3EN3vRMkYygCpMpgalJQIPIT20BQgEsdjJ47HK3oRZ9MV2Csn4fX5K0bh+T5/r2XW0/cBrKrQF4STJVZAOKHFf3cxVi59o7XmrLPO" +
                    "YvLkyfTu3Tv0rIUQ9OrVi8MOO4zW1lbefPNN5s2bR1NTE6ZpEo1GMU0TGewM2+TfBw1x8pyNNkYmnU6TTqdRSlFRUcHYsWM58sgjGTx4MLZts3z5cnr3" +
                    "7s2wYcN4+umn+fa3v80111zDypUru7j9rtE1tgb4WvsplrrIxhQW3QrKwm3BZkJFgyGh6REyG65AFuyDXfFndHQ0mjRSefjilwItTL8yVmdwMDEwsdz1" +
                    "pGt/hKx/kWjl9ejmFxCWiWlWkN54Dkbds0gFouBIIhW/RovizV592/UCtt0Pr/4xlLcYkv/BMy2Ecoh4oKwBeJXfRbjrcGv+jqGWIylHZ9aAPRYtM74x" +
                    "ECZamIBGKBctHGT8JKLdh5HZeBJ645UY1kRkdE/w63ZDQK+MlwICmbD+z9wkAdiXlJRw5513ct555wGQyWR47LHHuOeee1BKMWnSJM4++2wuvvhiLr74" +
                    "Yj7//HPeeustpk2bxvz586mtrcV1/X7ElmVhGEYetZNbYOV5Xh7Hb9s23bp1Y8SIEXzjG9/g8MMPp0+fPgB88sknPPzww7z55ptMnDiRH//4x4waNYpT" +
                    "TjmFAw44gMsvv5znn38+z9h0ja7RRel08D9GxIAb96OodwUfn/pn9ijogZfVrQeFRqK9VXjLx2FYA5F9XkLLbijlYGCElbohKAuNVA6OjEBmDqr2L1iN" +
                    "9yKswWSKj8Wq+SNK9kMnJqKbnsVOQyrRD6vP60hzKFophDA6Mk++t++tILn6dOz0dD+FU4KO7guWjc40IdyNmM5G0pE4ln0sKvUWKlOD7HYbZun12e+X" +
                    "OQbFl8N0hYMhI2hnPnr54ejEvlg9n0N5AgzQnsIwTP69Zg6HvfYjYvPraf3jhwgpUfq/t/jKsiwcx2Hs2LE88sgjjBo1CoA5c+Zw6623Mm3aNAoKChBC" +
                    "0NzcTFFRERMnTuSkk07iqKOOorCwEIDa2lrmzZvHnDlzWLhwIStXrqSuro7m5mYcx8FxHKSU4W6goKCAiooK+vXrx7Bhwxg9ejSDBg2itLQUgPXr1/Pq" +
                    "q6/y8ssv8+mnn5LJZCgsLKSlpYV4PM7FF1/MpZdeSjQaBeD222/n+uuv76J4ukYX4G/pf8ySKO5N+9KvqjufnH4f5VaRXyXr5++AkKhN16PqH8LsOw0d" +
                    "GYhULkEGfW7Vqo+fLq4wsNwvaFl/DRGzHKPlCZAxnOggzOZZEBuEdhtR0kbHJmGWnA1yiL/NN6L4Sfp0DPpaotzPUckpaG1gNPwVgUna1NgtM31pZxNw" +
                    "wBMl6LLzkUY/Uk2PE+n2ENIeTlg7kD0RQnso6fmyEMJGNz6JXnc+ot+/kZF9QHsorTAMi3mblrP3S5diLqon9fsP0K5A/ZdSOkFw9vjjj+eRRx4JwfaB" +
                    "Bx7gN7/5DS0tLRQXF4fgKaXEdV1aW1tRSlFaWsrjjz/O2LFjWbp0Kb179w559mCk02mSySR1dXVEIhGKioqIxWLt0jAbGxsRQlBQUMDtt9/On//8ZzzP" +
                    "Q0pJIpEIi70CMK+vr+fAAw9k8uTJDB48GIAXXniB8847j+bmZqSUXVINXaOL0mmL+KIwChFBhVVEkZEIOoX7/4dE62pUw1PY5dfhRAYitYMQEq39jBsh" +
                    "FFobBHn0vpSxxKv9g++FR45F4WA4GUx3DgIF6WUgXIQxDDM6ikzzf8Beip04GVQbSimbPy+0H0/QwkHYwzHt4SjAEQq59nJigLZBKxPteSirN7L4LLRd" +
                    "jmqdiXTWg7cRLfYE7fq7AyLZXH+Qygbh4GkXUXQKquFhRMNL6G77ILTO7gqgNFZAzIjQWmCioxa62fmv5OxN08RxHM4++2wefPBBTNOkubmZm266iSef" +
                    "fJLi4mKKiorCbB3wc/GFEJSVlVFTU8O3v/1txo4dy+uvv87FF19M9+7d6du3LwMHDqR///50796d0tJSKioqGDFiBI2NjcybN4+WlhZqampYvXo1K1eu" +
                    "ZNmyZSxatIjhw4fz5JNPMmnSJJ5++mlaWlpCbj4wOsEaKioqmDFjBpMmTeK3v/0tRx11FN/61rfo1q0bJ554IrW1tXnZRl/FOQ0opV35nQBKqS0ar46y" +
                    "oXLH1nY7QarsrqbDAgov+M4tnaO2793WERQEdnROOorpbGkNO5rx1dZ5yT2m4Lp1ts5dcY/k3h+de/hBSuaIbmQu3ZNjeu/Dayf+2m/8IXwBNISBbv03" +
                    "3sZbMPs+gRLdsyFZI49oIft+rYVfcZv8CHfd4Zg6A+ZgPPdzpFGONItwPY1Ib8IQDWgX0jKGqLqFaPFlaBFFCw+ws958FvyzP/116axGPiA8oAVd/zgq" +
                    "+W+0rsVIzUGVnYKT/Ixo42y0bPAbWUmg4m5E0akIKhDobFMWhdAmYZBYe2hpoFveQNXeh9nnadBm2Ae3Md3C6Ge/x4q1a5G//RBd3RoUDP/X0TgXXXQR" +
                    "9913H0II1q9fzyWXXMJ//vMfKioqOg2ASilpaWlh1KhR/O///i/r16/n+OOPp6mpCSkl6XQax3HQWmMYBo7jsMcee/DBBx/w/PPPc9FFF1FYWIjjOOEN" +
                    "a1kWsViM2tpaLr30Um699VZeffVVvve97+XtMDp60IL5brnlFr773e8C8OGHH3LcccdRU1MT7gz+28eWdiw7upvJDZh/FeveWlX69lat7+r378h53NXH" +
                    "tLPneeuFV6VRkILeCT9g67P2fhqkEOCm5kPhCbiyJ6ZyoR2/LnyBMy0Q2gBVTab2DiyVQYgUIjkXYYAyXbxMBlN2w+12Kqr1EzynCavbHzAjE1FKA1nt" +
                    "HK2yXLvIw1Hfyw5aHErQBp6wUaWXIUsvQzpLSa0+gmjzTCxnARiNWQkHE8MDd+PVeDX3Y/Z9DGWNRGoHlJknFaGFRKExIvvgmP/EVLUguhNoBUVNmwqr" +
                    "iBWR9YiY+V/H5Ni2TSaT4fTTTw/BfsWKFZx//vksWLCAysrKEIw784JM0+QXv/gFlmVx/fXXU11dTUlJCZ7nYVlWXgZOKpUiGo0ipcSyLOLxOCUlJXnp" +
                    "m8GroqKCBx54gIMOOojjjjuOSZMm8dxzz4Xf3ZHnats2lmXxk5/8hMbGRq666iomTpzIK6+8whFHHBHm639ZoB9891FHHcXll1+O67qdenzbO5qampgz" +
                    "Zw5PPfUUy5cv7zATKahFOPnkk7nwwgvD+ZXyxQAXLlzINddckwc8bbOyxowZwzHHHMOee+4Z0no7MzzPwzRNPv74Y372s5+F3q1SiosuuoiTTz4Zx3HC" +
                    "c2dZFtOmTeNXv/rVNoNucDyVlZXce++9RCIRVIAZ2Yywq6++msWLF+fJghxxxBFcccUV7c7TihUruPzyy3fIaN51110MGDAgpCCDY/rTn/7EG2+8AcBp" +
                    "p53GueeeSyaT2WH5kbYjmUyycOFCnn76aebNm4dhGNuQllmUBfyCis1bOrG5qbimGRE7NFt85UscCN2GV0eDZyJMyNT8EYWEaF9EyyLcaG8fcFPLQdeg" +
                    "WQHVc/GscZiVf0FHRqO0g5YuUkuEMjAMubnXbbttUvB3n/sxUBieQksJwkSIMrzmDzElaDuKZ/ZDeEl04huIeD+8xreg8SXM8hFZyki3U/2USqHMQozY" +
                    "CLTXgrCyYptobMOiMlIEFhiFkSzgZ+Md/wWcfSaT4Zvf/CYPP/wwQghWrVrFmWeeyYoVKygtLd0i2BuGQW1tLRdffDF77bUXDz30EG+99Rbl5eUhddJW" +
                    "CrntNjlQ1uwIgANv/5ZbbmHChAnccMMNTJ06lebm5lBIrSMDJISgtLSU22+/HYCrrrqKffbZh6eeeooTTzyxnWTzl0G7nHXWWRx33HG7/PvPOOMMrrvu" +
                    "Oi655BKeeuqpDmkqrTVnn312h/M3NDSE1y74XPB7//79mTx5MpMmTdplIJQ7vvjiizyjqLXmggsuYN9992333lmzZm2Xlx0A64QJE/jWt77V7v9TqRS1" +
                    "tbXh+QlonFNPPbXD8/TKK6+E4L8tzkGwzl69evGDH/ygQ7pm8uTJ4e/nnXcexxxzzJfyXF933XVcffXV3H333VsGfAFQ6KNZt3i5/wAJcmgbD5VeiVUQ" +
                    "9dM3he7wWzQCpMbzajFqH0EVjkV5lWgWQeEpYA/ASy0H53N05iMMYyyi8jeo6GikSoGwEDrme/WGYOqaOby66N8oK05KKmIqQ1QLvj/ubHrFC3OSNrOZ" +
                    "O1L4AWSzL7Eef8dtfhq34VGENRQz8Q107Z/xnCkYdQYRrxEVOxaFwPQUnumLwoWXS2jwQEoT5S5DZ3ohrIH+udASQxh0i5WCCbI45tNF/wWUTvCQDxw4" +
                    "kKeeegrbttm0aRMXXHABK1eupLi4eIt8txCCdDpNnz59uOKKK9iwYQN33nkniURil3nPSini8ThLlizhj3/8Iz/96U+5+OKL+dnPfpZnVDrjf0tLS5k8" +
                    "eTKJRILvfe97HHvssdx5551cfvnlIY21q0cATv369dul35trQEtKSnj00UdZvHgxM2fOzAMlz/OIxWLstddeeJ4XCh0GXvbLL7/c4X1w4IEH8txzz9Gt" +
                    "Wze01jiOE8Z2dsXIZDL8/e9/z+Oyq6qqGDZsWN4uJaD+Ak94ewr2APbbb78wvTfYARmGwdtvvx1SeoGTIYRg3Lhxeecp+Nwrr7yS973bOv9ee+0VHm+w" +
                    "Y5BSsmbNGj755JPQ2ejbt+8uuzcymUzersW2bf70pz+xbNmyLQB+9ryKIhsElMdLQ49XI7JNyxuQqSlo9xiUPSorZdz2KwPuXuC5K7H1aoy0xFTFaFOg" +
                    "6/+Oqw1kbG+kVYlrXYmouBKPAiQZRFb4zJdg8xuM/GvtLH4z/e8UFVjYqpVqUQyuy+GDDqFXYk+08tqkVgo/50YrtD0Ko3wUbmoGMjUb5X6CoWox0gZa" +
                    "emjPwLD746HRRsSXShY58g0ahCHQOBhN/0TLYkgcnd3I+Cete7wMpPID3gDi6w32wc0ZiUR46qmnqKioIJ1Oc9lllzF//nxKS0u3GtwMuPsbbriB0tJS" +
                    "rrvuOjZs2EBZWdkuDYx6nhcC3GmnncZ3vvMdnnrqKVasWEE0Gt2i96e1pqysjFtvvZVu3bpx0kkncdlll/Hxxx/z8MMPfynUTvBA33jjjZxxxhnhudrS" +
                    "GoPq5I7AzTAMDjvsMCorK0NqwnGckLbK9WYDL3Po0KH0798/pGoCEM1kMnz88cehYQoMwdixY3nllVdCI28YBpZloZTijTfeoLa2dqd2RKlUiscee4zZ" +
                    "s2fnfc9ee+0V0nnBOoUQVFdXM3/+/DwDui33CcCBBx4YUmi5xz5jxoy8c6SUon///gwfPhzDMELADH4G52l7Dc4BBxwQfk/uOmbPnh1WizuOw5VXXsk5" +
                    "55yTV5eilKKpqWmb55RSss8++zB27Njwegb3tJSSn//8550DfrZWClUcwdQm3bOAL9AIJcEAkf4C6SxEpBag48dlC7U0OrdiEtcXWBMCU2syVoyIsxxP" +
                    "gFJxDNGI7QEN/yIZ70uk9y9AJDBVK1pYvmJmaH2y3KXrYRV7PHNgkvFF67l9scEf5paRSddv7TIgdIYMJrLw+3gt52PQ7Gf34PkVwdpDbbwBWXsXutt1" +
                    "EJsE2gtpHak1ShgobzUiMwvtjM9Gvk2fRgIqYyW+ASiwst/53+Hd/+53v2Pvvfcmk8lwyy238N5771FWVrZVz1dKSTKZZPDgwZx99tl8/vnnPP/88+2y" +
                    "eHbFCB7YxsZG7rzzTv7yl79w4YUX8qMf/YhYLLbVzwohKCws5Prrr6d79+5MnDiRu+66i+nTp7Nw4cJdnq4ZeI7Tpk1j2rRpu+Q7Dz74YKZMmRLSWAGQ" +
                    "TZgwgUQiQUtLSwgySin222+/PG81OA8LFixg2bJlebx2IpHg8ccfzwN7gE2bNnHqqafy7rvv7lJjmFthvf/+++cZnwD4Z86cSV1d3TYb5OB7y8rKwrqR" +
                    "wMgGP4NrEdA5SinGjRtHLBYLdwHBOlauXMlnn322XQYneF9AT+VmVBmGwQcffJBnQKZMmcKUKVN2CS377LPPcvLJJ4fHEdwfo0aNQnaE9H7mi4aIiZsw" +
                    "KDQjPohlQ7CSrEZ8eg7oItzMHAzt+Do37R6yrHSy1qiWt3w1TVegii/A6P0cquRKUiUn4SUOwir+IdIYgHA9PyNHW+2NENDc3AgaBlm1VNhN9Ilk8HSK" +
                    "mnSSreOrhYGLWfg/6D4voc2RuHY3VOExCDkUXX4pVFwBsi/pjT8DtSmr25ndYgrhq0an5uFRgnLX+HGMnAByUJGsiqyvPXMfgH0QrPI8j4ULF/Lss88C" +
                    "hMGzLW1lhRAkk0nOPvtsotEo99xzDy0tLbssONkRiBYXF4fyDaeccgqDBw8mmUxu0XsO1pNMJtm0aROvvvqqH6YqKuL+++/f6nHujJEKisoMw8A0zS2+" +
                    "goe07SuoTl6xYkUYm8jdRcRiMeLxeN68AAcddFCHYDR9+vQ8UAiCpnvuuSeO42CaZjjPTTfdxLvvvptXIb2962/7CtYXrCdYZ25rS4D//Oc/W90ZtXVA" +
                    "gh1DeXl5XrBWSkl9fT2zZ89uB+DB/G3X9cknn4T31rZ424F3XllZyejRozs0OG0BP/DGO3pt7X4JznkkEsF1Xf7whz90SD8ZhtEB4CPCWikZs9BxSYld" +
                    "QEk0kQO6fhNz1ToXUfxNtLsWvE0+Vy/a5rW6IA208xmy6S3M6KF4UmIYQ0g3/y9eeiYm4Io0InEECo1napTwPXuJyl0ZAPVOPXEhEcJDK02RkQEl2JBp" +
                    "zeejOrK8QmBoE6UFIro/njTxoiPAHIKiHjKbgFJUj28jjT4Id+Pm3E8Ig6+idRaq6ACkkmhnCa7A7wMAVMZK/QB2geU3Wvyaon7wECQSCe6+++7wgRg1" +
                    "ahRTpkzh1FNPxfM86urqQs637UMnhCCTydCrVy/OOOMMli1bxj//+U+Kioq+1HTHYFdx7733Eo1GOfXUUzsE/FzeubGxkaamJiZOnMhzzz3HbbfdFvLh" +
                    "Bx10EJdcckl4nF8Gl++6bsgnb+kVBK3bvgKueeLEiUQikXD3EIBG0F8g1zDats348eM7BMz33nsv772GYfCd73wnL4gZ/Hz33XfzgDhX/qKjdQaeewB+" +
                    "Hb0vFxzLysoYO3Zs3pzBfIE3vq3edfD53B1D7s/Zs2dTXV3drv3mPvvs0+F5CubfXoMzduzYMIMsuE6GYbBp06Z2BmdL53Nr90vuPWMYRlhd3tbwr1+/" +
                    "vmPADxUjExY6IimziymNFKBDYUoLyEBmCSJxsl9P5X6RNRRtLkq26EqlZiJSH6KliZQm1NyK2fBX7KapyNoXsazRYI9CaI3Upt/Hlmy1bvjw+j/rMs3Y" +
                    "UmIbvg5+sSlAuNSkareG9yCyefpSY6pmtGdiN76DrLkX092AbHkWWX0hLD8bKQrAGpTl5oN1+DeJTs/CLjwKZByVWhI8NgCU2YWY0oaIzLUVX0vvXinF" +
                    "VVddxZAhQ9Bac+WVV3L77bfTq1cv7rzzTl555RXOPPNMbNumpqaGVCoVeiMBbdDS0sKRRx5JUVERDz30EI2NjV+ad58LUIWFhbz99tusW7eOU089lfLy" +
                    "cjKZTMhVB3n+gYbPIYccwiOPPMKzzz7LIYccwuuvv85RRx3F1KlTAfjpT39Kjx49wq38rjJMHXm9O/P9W/JEAxomAMuhQ4cycODA8JgC0Emn0yGPHXxX" +
                    "r169GDRoUF7xUfDd3/ve98JivCCzqrNXwIkHYLSlYGcwz5gxYygtLQ0NRbDOhoYG5syZE56zbdk5BOs/8MAD29EpbXcMwVw9e/Zk5MiRee8P7uHp06fv" +
                    "cMC4o+s0e/Zs6urq8nYM23psW9oFBM/Fueee225eIYRPA3aw/9zMnRRaYEKVXYItLDyt/NCpNJHualxdjxk5BG29gU7PREYP8tMiRV7I1m996C5HUwet" +
                    "76M9BV4aw7RRdhWuVYhRdmXW3HhZPrx9DFn65VvUp1NEpCaCBwoKI2kQguqWhrydQCd4jxAahQdGOXbVLWTWn4vp1WEICyH9DCSRMVElpwERXzkzzPGX" +
                    "oDbgqpVYkeNx7c8R6QVYheBlC85KIlHiRoRUzEJEBKS/fogfBOj69OnDVVddBfipZ08++SSmafLSSy9xwQUXcPbZZ/O73/2Oa6+9lscff5xXX32VxYsX" +
                    "43ke8XicaDRKNBrljDPOIJVK8frrrxOPx78S2QLTNKmpqeGpp57iqquu4uCDD+bll1+moKCAhoYGlFL07t2b0047jTPPPDN8oF9//XXuu+8+PvnkE9Lp" +
                    "NL/97W/Zd999qays5Cc/+QmXXXbZLjFYbascd1U8IPBc24JoYLhyPet99903NHwBwEgpWbRoEUuXLs3bIZSXl4eUUC7oKaW45pprOP7449m4cWMeHbOl" +
                    "tS5cuJCXXnopzHDpKD4SzBMYsWCHlct1b9y4MS8Quy3nyTAMxowZ0yGd8v777+dRbZ7nsddee1FYWJhnGIOCw7lz5+4Qf9/2OgXnLKBzco3+rrpHrrnm" +
                    "Gs4666y8wDf4EiZ/+MMf2iOrEL4+jkBDsZ+S2T1SHBoDwy9nRTuLMWQR2u6NaQ2H5Dwobg/TQpiganAzazCEgeHW4ZWehy44EUEjwmlANX+ItMowdbaN" +
                    "bWdpQ0KSURmanDQREyLCL3YqMFNgCGpbG/N2AluAO6Q2/KrZ+PGYfd/BW38OsmkuwhIo5aHjQ7AiQ3N2GUGbRcBZgOHFwerjC7q1ZrecQmIApZEC4qZF" +
                    "S9RCxGxIp7ehXfxXT+copbjuuusoKyujqamJP/zhDyQSCRKJBOvWreOmm27igQce4Nvf/jZnnnkm1157Lddeey3vvPMOL774IjNmzGDhwoWMHTuW0aNH" +
                    "89JLL7F27dq8rJ4vK7c98OIKCgp49dVXueKKKzjppJN44oknqKysZP/99+eEE07g6KOPJhaLhZkhTz75JLNnzw4/W1JSwsyZM3nyySf57ne/y3e+8x3u" +
                    "vPNOvvjii50K4AagceCBB9KrV6+8bb1pmixYsIBPP/10m7NdgrX069eP4cOH5wFGAJABkOTy+wGQ5nrsUko++OCDPHAFX+AulUoRi8XygqnBsQwdOpSh" +
                    "Q4duV3D5oosuYsqUKXznO99hzZo17Y43mPuAAw7o0Ii5rsukSZOwLGub899d12XQoEFUVVXlUZeGYdDc3Bzm9OdSV8H8bQPGn376KY2NjdtV8KWUoqio" +
                    "qB1FFfwMdhi5x7/ffvvRr1+/7aZBg/lKS0uZNGkSRx11VN71D47jlltuYdasWZ1n6WhAFMdAKCoLSgnIGkuDK4DkErD6+f1lI3vjtXyAJO13pcrZLQgJ" +
                    "Xut/8EwDaZXiZmr8frEijtj4e0jNIxLdHykLwdNouWX+NO2laM6kqDQElvRAQ5H0ME1BbbJ5i9vHzccmszsZhdZJdP2bCNELKvbDbf5ftFWA4bbiLD8C" +
                    "ev4FK34yqGxDdmGgk3MRZi80YNgD8ZpeAt2CII5GEbfjlBgFrI9WY8ZtqE/zdUL8XPA4++yzAXj66af5/PPPKSsrI5PJEIlEiEajrFu3jttvv52///3v" +
                    "HHrooZxyyikceuihHHrooWGaXuBJvPTSS9TX14c3mWVZYZCvI06xI42XAMjbvif4d8BpZjKZMHto4cKFzJkzh4MOOojHH3+cgw8+mKKionD7/Pzzz/Pm" +
                    "m2+ycuVKbNsOFTyVUjiOQyKR4IEHHuBb3/oWpaWlXHPNNVxyySWhd7sz2UQPPfQQAwcObPf/l112GZ9++mlewdO2XLOJEycSj8fbZZIsX76cBQsW5HHs" +
                    "tm23yxIJfuby9wFArFy5kk8//ZR99923XSyjoyK5bTkHAIcffjivv/46hxxySEhlBHMqpSguLm4HjsE9dfzxx3P88cfvkhiKYRjMnz+fdevWhfdULuB2" +
                    "5I3n8vfbU/A1cuTIsIYhN2Dc2NgYGnrXdenVqxcPPvggRxxxxC7bBeYad6019957L7/85S/9AG9HQB/kk9tFCTKGpGe28Ymv+pi1HOmFEB3skxjRwXi6" +
                    "GcNdizAHEJL9QYAzORNLFiBixyBSj0L903jJf2N6zTgSzMQeKAqQOFkhNLMTCwQpN0WrkyZhKwzhSxcXCkXU0tRmWvH8zredKOZvJpp8bsdCJj/Bqb4O" +
                    "o/QEMjiYKoM0R+CVHgIt05Dr70LscQRaJNBkAAtSC5Hxof73Rwbjag/pLEdaI1DawzZtKq0SFtgKGTNx+XqNwAu68MILKS4uprGxkUcffZRYLJb3IIKf" +
                    "mx94yM899xz/+Mc/GDRoEIceeihHH300xx57bPi9P//5zznxxBP5+OOP+fzzz1m5ciW1tbVh/9rgps/lY3OBO2hykkwmcV03pENy1Tij0SglJSX07NmT" +
                    "4cOHs9dee7H33nuHipjHH388n332GW+++SZTpkzhs88+I5lMEo/HQ44498HVWhONRlm6dCnPPvss3/ve9zj99NP5xS9+wdq1a3fIyw8+M3jwYPr06RP2" +
                    "AMj1Wt96660d2sp3xN9LKZk+fTrpdDoESq01Q4YMYY899mjn5TqOk5d/nwtUkydP5sUXXwyDmbnGekfjDplMhlGjRvHTn/6UK6+8Ms/j9TyP0aNHU1VV" +
                    "1S52sjMZU22/qy2dEqR4KqUoLy/f5myabeXvAzqnLUU1d+5cP3iavZdfeOEFJk6cmOfZ74jQXlDYFVyv3JTSyy67LCzC6pAsz7I2iJIIYNEzm4Pv59ib" +
                    "mDpNSi/DipzoL9DojTCjkJ6DMAdkM1l8+QUNuKzGrHsbVX422BFE8lVMB5RVjiiahGsOwdQeSmq0thEdeMLBX5KOS8pNE5FgZu+HhKmISUV9poWUzpAg" +
                    "Ap1Cvt4cnAaEVYGI9kU0vIphKAwFXvpldPW/MWQ/PHsUZHPsBRGEzuC4SzCLjvBJHrMf2ihGZeZh2CPQSmFKk4poARgCWWBtnu5r4OAHF76goCD07t94" +
                    "4w0WLVpEaWlpuy1l7oNfUlKC1pply5axcOFC7r33XiZMmMAzzzxDdXU1yWSSE088kRNPPDH87KpVq1i2bBkrVqxg1apVrF+/ntraWhobG0mlUjQ1NVFe" +
                    "7ldxR6NRevbsSbdu3TBNk4KCAsrLy6msrKRXr17079+fPfbYg379+uVlImzcuJGpU6fyjW98g2effZYf/OAHmKYZiq4Fhqyz7bJSilgsxpNPPskZZ5xB" +
                    "SUkJ55xzDpMnT94hLz83/9227TxdFiklixcvZsmSJdsFJIHntiP8fe5uQAjBkiVL+OKLL9rtnAzD4KWXXuK2227jpz/9afj37fXqTdPMW18AeOeeey6/" +
                    "+MUvqK6uztvZBd51rnca8M651bDbOr+UMqRz2gJx2/z7oNCsrKwsj7+XUlJXV9dh+ua27GqCY2q7w/jwww/Dfx999NFMnDiRdDqNbdvhGh3HYcOGDdtM" +
                    "5wT6U8H8gcHwPI/+/fvzne98hwceeKBjD9+fVCMlOKUWQlj0yuroCDRKCgx3JYbjIKxBvkyOjGKYQ1GpWcjESZu7DwqBRCNVNXhL8ZofxfIMlKggUzUJ" +
                    "0xoFLZ+CqkMLidDuFrxy/0SmXIeMlyFiku2kBXHhEDddmpwMSS9JwoxsBVw12a4laHMQlN2IXn8thsiglYU0MxhuC443D9HnOjQRtHb8ql93OVrVoqMj" +
                    "ff0cYRKxBkDqM7wCsrsb6JYoAUNDUexrl5njui5HHXUU/fv3x/M8nnnmmU61aNoCP0AsFqOwsJD6+noKCwuJRCI8+OCD/OlPf2LUqFEMHjyYPffckxEj" +
                    "RjBkyBAOPPBAvvGNb2zxu7XW7XYMHY1NmzYxa9YsFixYwLx581iwYAFffPFFWDU6YsQIEolEGDjeEtDnzh+LxVi0aBHvvPMOJ5xwAqeffjq/+93vdqhw" +
                    "bEv571JKPvzww9AIbI8uS9++fdlzzz07pD5ygSQAjoMPPrjD3cAHH3yQJ07W1qjccsstLFmyhJ/85CfbxdlvLZ5RWlrKkCFD8lIigXbZNEGB2H333cdN" +
                    "N90UGs1tpVPGjRvHW2+91S7jJ5VK8cknn7Tj7wNwbsvfB+mb21PwFUhZtE2FDX7mUmmHHHJIuLbgs4Zh8Oqrr3LeeeeFUhBbO2bLsjjhhBO46667Qkco" +
                    "1+BOmjSJBx54wDfGHYGh0EBMkimwSZgRqkIP3/ebVWYZQhSB2R2h02gRAXsMJN/OUj9gaF9ZUqv1uFoQESaR9DIy9jiMbjcgmj5EN01GpFdjVvwBF4GJ" +
                    "QmiVV6nbdiSdFNrziBpZakaDJQVFpkF1Jkkyk/Y7DuptC94CyPiBOPFeRJoW4kQVpqcRyoPKqzAKzkJpjRAKIcBLz0aKSrTRD+G5CMOEyFB0yzTfIGZN" +
                    "VlW0xO91m4h8rQA/eMhOP/10tNbMmTOHmTNnkkgktisLwfM8HMcJc5fnzp2LZVksW7aMzz77jH/84x8YhhFSKeXl5VRVVVFRUUFlZSUlJSWUlJRgmiZl" +
                    "ZWUcdthhrFu3jhkzZuB5Hk1NTdTX17Np0yaqq6tZv3491dXVeV2yhBDYtk0sFsNxHGbPns3BBx9Mt27d2LhxY7tmK9syXn75ZU488URGjRrF3nvvzQcf" +
                    "fLBdkgvh1tk0Q/687fY81xvfHppg77337rASdMWKFR3y953llQeg09H8Adg9+uijPPvss+y3337sueeelJWVbVXmVylFYWEh5513Ht27d+8wvTW4JgGH" +
                    "XVhYyN577523zmBdb731Fk1NTdu1e9VaM2LECKSUoeREbmXx8uXL20k+tzU4HWXTbE+F79ChQ+nbt287g9Pa2hoaHPDz/nN3OsG877zzTtjwZ1t3Ng88" +
                    "8ABjxowJFVlz4y+B99+JPLJAaA0xGxUxKIsUUGIXhB6+j7pfQKQCIaKgk34OS3wYuvERtG4AUQzZRiheZjmmMQCv6ARo+AeysDs0/xtZdzemBi/SG09E" +
                    "MLSH1tEO6ZxcIqZJNYMSxAw/iKoAQwqKJLS6rSSd9DYin/IllDPL0KtOwjQMVO97MOofQrGEjGjFan4PHX8VGT0aL6sfROtMhD3Ir74VSd9MxYahGp7B" +
                    "VA1o/IBgeTS7xYoaXxuwD8CovLycgw8+GCEE//rXv2htbSUajW53hoBhGOy55554nsfSpUuJRCLhKxd8ampq2LBhA3PmzMkL/AXyyAMGDGDmzJlMmzaN" +
                    "c889l9LS0rwHMjeX3bKssKVirqcWAP6hhx5K3759WbNmDZFIZJsfmGBrPGPGDFauXEnfvn054YQT+OCDD7aLSw4e0sGDBzNo0KA8DzdQstzeQqIAFDry" +
                    "2IUQfPTRRySTyZC/9TyPIUOGMHDgwHag4zhOmH/f2fyBQUmlUrzzzju8884723VfvPXWW6HYWS7NkE6nWb58ed57R44cSY8ePcLrGKyzpaWFmTNn5klE" +
                    "bOvuNThPuZkqQZwjuA4Bf99RNs3OFHxprZk4cWJo0HKzoBYsWMCqVasA6NWrFyNGjGi3WwO/X0NQS7Itz2Rg1BYtWtShgxcYTSEEspOQJrLABltTaRdS" +
                    "EElkuX0/7dBzFqLsPRCAJwzfm7b2wBNAeqFvAITjg7RykQ2vIxMH4sT2QDZPw6i5Dwk4xd+CshtJk0KiQXhbSMv0R0OmFbRDxPDNgNKAcElYCu04NDrJ" +
                    "NiaivUEL9yoClLsET32BNKLo+kfBWYawx2CU/wwl03irzkKl/wPSROGhM5+jo2OzMxhoBYY5GGWkwVka9tstixf5rRiL7K8N4OemoFVVVZHJZJg6dep2" +
                    "AWMuKMRiMQYNGsSqVauoq6vL6z4VPFDBDRmPxykuLqa0tJSysjLKysooLy+noqKC4uLi8H3B34L3lJWVUVJSQiKRIBKJhA9/7hy5ueUAAwYMCHcA27Pz" +
                    "sSyLTZs2MW3aNLTWHHHEEdvdICU4x/vuu2+eVHFwfhcvXpzHn+dmKnX2Cj4f8PdtA5uBRETu+wP+PggYB3MtWbKkXfygozkDkAxiIdvyikajRCIRli5d" +
                    "GtJDARWotWbmzJmsWLEib/3BLiQ4x8E9M3/+fNasWZMXR9jSK6CCIpFIOzqlLYDn/m2PPfagqqqqw4KvIH03+K6tvQJ6LUjxbJsF9fbbb4drHTt2LAUF" +
                    "BXnpukHe//bo9uTy/kceeWS7nYoQIiwck1J2rKUjAVkYBRt6RkuISgvPU2hhIHQLylmGYY/OUvUCoTy00QNp9sBLz8hOlg2KGiaapaiaP2HKCvAacGJ7" +
                    "oHr8Fll4HG7jg1iqhqDbomiD+ME/A3mChlQK8LClnxfvUygeBZYC5VGXatoi3G/Op/dTM43ocIQ1GtEyB52ZjlDV0PIesv5/kaVnIcwilLsKA5+ewl2H" +
                    "jI3JWZtCG92B7ujM7JBGqoiVABIVAP7XJGALcNhhhwG+HvmiRYu2qjLZ0fe4rktxcTE9e/ZkxYoVW9Sx6awUP7csPNeQdCQvkFvd2RlYr169OgT8nTlH" +
                    "06ZNQwjBiBEj6N+/fx7fu60j8DJzH/oAdIKspK2BWPByXZdRo0YxatSoPFVLwzBYs2YN//jHP9q17GurSxPMn8vf50oQd1Yx67pu2GR+a69UKkU6nea8" +
                    "887DsqzQ2ASA96c//SlPrCz3PHWUDhmA7/bc24MHD6Z///7tMpMymQwfffRRuzhHLBbLcwyC+Tdu3Mjq1avD+zaIB23plU6nqaqq4qijjsoTtQt2Nw88" +
                    "8EA4V0Ajta3C/fjjj2lsbAzP/9buDaUUmUyGU045hWOPPTa8L4J5M5kMjz/+eOeUji8CpiFhgaF9LjpLnkhMlLsGoVoR0cEoDUaoNGkg7CGo1BxkMb6W" +
                    "jAYtyzAoQ7vLEZkVuPH9MbtNRm/6Hbr5RUwNwpqA0tUgKlBCdVgpGzzmDakmwCMus6mV2creQlOD9qhPNm3+gOjEw8/2XVTCRcjeGBW34K45Aym6o2QV" +
                    "hp6J8mag185AxI5Exr7pf116CYo42IOy3yEBF7CxzD3Q6UVkGR3K7AJsaaMKTIQEVKjCs9vQP/AmJk6cCMDMmTNpamrqMDtnW6ih4uLiEGhzH6LdFYyu" +
                    "rq4GoGfPnjucyheJRJg7dy7Nzc0UFBQwYcIEli5duk20Qq7+eEfeOMBHH31EIpEIAXFbtuujR4/mnnvuCfVzAiAQQnDVVVfR0NAQ7iba5t+39XI74u9j" +
                    "sdhWg/bbsrMpLi7m3HPP5YYbbghjAZlMBtu2ee2113jqqafymp3E4/HQG29bJ5BbnLQ9mVETJ04Mdza54m9Lly4NG67kHuf69evJZDIhLRLsSgYMGMA1" +
                    "11zD888/v9V+usGuaMiQIfz617+mW7duoWEOjv+2225jwYIFoRxyZ3n/n3zyyTbfH4ZhUFVVxZlnnsmNN96Yd685joNt29xxxx0sWrQovD/MtlgospQO" +
                    "JTZoqIqXZb1ZD4EJmRUoGQGjp9/fVkvAp3pUdCSifjoKDyNbgKV1K07ZSYj6J7DcRkTBCajWeYjGFxEm6OghpGUM26vBMCpwhZ9HH15I7XvxXnYP0JBp" +
                    "AgFF0vUjs1qBhFLDA6WoSdVndwQa0a5y19fc8dszKoS2wf0cdJJ00dHEKyYjjDLU2lPQyf8g7L0R3X4PZjck4KXmQKQfUhSitUJgoAOVzOhAaH47tDTF" +
                    "kShFRpS6aCMiYiCSHp7Mbgv0Vw/4gbdTVVUVZl4EFYc7ajyCYNDGjRt3K+AHD2prayvJZJKKioodAjCtNbZts3btWpYuXcro0aOZMGECTz/99DafY6VU" +
                    "Hn+ey88qpbj11lu56aabtvlcmaZJ9+7d8zzx4O8333wzzz77bAhwufn/bfPvgzhHwN8H31NZWcnUqVNDWYEdvYZBI5bcoraggG/OnDmcf/75ed6+1pph" +
                    "w4bRp0+fdvx9KpXK88a3Z3REp0gpmTFjRl5mVDDX8uXLmTp1KocffjjpdJpIJBIK7t1xxx386le/yqs63tLxB7GrwCAHxv+xxx7jF7/4Rdg+tLKysp3s" +
                    "Q7CTufTSSzn//PO3uqMMzllFRUVezCx33tdee42bbropLxZgduZJi9I4aEnPLOAHzHcmvQQiPZBEgAxI6Zc6KRDRkXiqlYizHG0N9I1Hph50N4yqX+Cs" +
                    "uQaj+R+kRRrbAGHvhy45AVn7T0SZQAt/QbrNenJPdUM6CVr7WTqBMpmWFFseoKhPt7SjgjaDvv9ef2MgUM0vw9ofoKJVxLvfjbCG+vr7RZejWpcie92N" +
                    "sEegdRpEBDfzKTI2LCfoa2RbO4KIjiLV+Ci2Uw9WKSWxAgqsBDVRExkz0UlfU1/vJmonoAEGDRoU5hwvWbKk00Yb2/KAB7or25tR8GV5+MlkkqamJgoL" +
                    "C3fYYw209hcvXszo0aPDNMhtrbJUSjFhwoTQy8qlJLTWIXhvz8hkMmGGi2mapFIprrjiCu655568OEHu/MF2PjebZ9GiRSF/n5sDP2zYsF1meFOpFLZt" +
                    "I6UkEokwZcoUzjnnHDZu3JgH6kop9tlnn5A2yu1GNX/+fFatWrVdDdQD+mj8+PF5yqJBLCHQz2kL3Fprrr76aqZOnRo2X8mlS3L7B2xtBFSdZW2Wdf/9" +
                    "73/Ptddem/f5kSNHUlRUFOob5Y7Kysrt3pUGBXdBQaOUkkcffZTvf//7IdCHIm3tL5pPqOgSGwyTnonyPG5dOPMwIuNzyAnhtw7RGsxBCFmCSs/O8VAk" +
                    "Ru1f0elqqDwPkjOxWhehik9HRUbjbfwNtrsWZHcQfrOUdjFWNufc12daAUXMCKrDfLqk0PT1amqyHL6ic10eLTKAgevMQuhVWMImU/Mz3FXfRK87HdRq" +
                    "RLcrcar/hufMBxEB3YhKL0Pae+WtKzQq9jAMbYJajgZK7AQlkSK0bSCiu5/HD264IHOktraWtWvX7hDgBw9i0HAkmUzuVjont3jIcRxisdg2CXxt6fsC" +
                    "YBwwYEBepsW2rOOkk07CMAxs2+5Q0XB7h23b2LZNY2MjTz/9NPvttx/33HNPO1mGYP4TTjghb/5A3uLTTz8Nde6/LI2jaDSKEILZs2dzySWXcPTRR4eV" +
                    "pW3PYbDOSCSSt96PPvooBNvtCZQPGzaMMWPG5B1zEOjvKDMpMAZz587lmGOO4cMPPwyDr6ZphoYrF0i39AqC1+l0milTpnDUUUflNYgP5j7mmGPC97dV" +
                    "vdwRRy4SiYTn6uOPP+aMM87g3HPPJZlMtqOwzPbuvZ/ArhMmljSpzDbzQBhonYLMOmTRGX4dq5C+xyw8n8sWEQx7EKQ+RxeAVKCNnmjDQdTchhEbgxsb" +
                    "jFl4LjqzHl13N7b08IpOQMoitOegDYHU+VHbQCkToDHTAkIQld5m1NWCYkuAUNSkG7MGQuR5+m02yr4ljh6MNqPo9HTstEZ64BogGp5GlZ2Cju+D2/gO" +
                    "0fIRKOcLLOUgI8PJt5U+CaZkCYbsgU4tREf2ImJEKI8UgKkxIxab1XR2b/Q2CGitX7+e+vr6nXr4A5Df3YDfdk1tKz135DuCAHBQO7B+/fqt7mICb+q+" +
                    "++7jmWee2WU0l5SSmpqa0PMNKJ22HG8w/1//+ldeeOGFEGCCVMSZM2eGoBes7aWXXuKYY46hvLw8jPHszHlzHIdFixYxd+7ccGfRFuyDdd999908/vjj" +
                    "7dYZgPO2xpWCa9LQ0MA555zTTqYglUqFdQptjU4Qa5g+fTr77LMPEydOZMCAATskIyGEoKWlhc8++4zFixeH1ymX4gF46aWXmD179k6f77bnffHixXz6" +
                    "6afhPdNR3KEdh4/W6KgFBSYFZoTKrIevhYHyliN0Bhnpj58F73P3fk6650NgZBiq+T2f09fK9/oLz0LW/hHSszHNQ/Bi+yA2HotheHg6ii44FE/VIUUx" +
                    "0svfdwgt8IQKAb/JaQRhETcyvhnQCqSm0ACER206K6AWNE9vs2fwKR6BSi+F2DiIn4pseAQVSaCMNAYSDIVV/xyZ6CpkxW0+RCcXIIwYGL3beOoSLbKL" +
                    "tvujU58hiv2/d4sWgqGQcdOnpsRmI7a73P2AUgi07ben4KqzBy0ej+9WOqftelzX3Snv3jTNUJK3pKSEsrKybQL8YLz++utfKjWXC5odjTfffHOrNEDu" +
                    "+crNmd/VNNuWQPu1117bpnVu63Vfu3Ytjz322A7FowKj9OGHH4ZVyzsLwoFmVdt1vv/++yHF9FWf9w4rbWXEwIkaFJlxKiKFoEFK0OkFOGYUU/RA6+BA" +
                    "jOAI/Z+RETiNj2OqWpBlKGc9uuw7uF4TovHvGO4cxKZrkSpF2ion0v0OUi0fIYwyzOjRaL/Eqf0JBBQeLekMSIgbKsujZykdywEhaEy2bgb5DjJ1lPBA" +
                    "WOCsJZN6i2j335BJz8DwFiKkiXAB4eIpsOzjIX6or4jszAZ7uJ+emafTIwAXiYmOjcBtfh4rW9TlG0vtB8CD92q9W8A+eHgqKnyZjKCL1Y56GEHHKfA1" +
                    "cHYn4AcUU9DuLZlM4nneDscngjzsgFMuKyvrkP/d0ud39Y4nl1fe3vkDDrozbf5dud7cjJateeidzbujPQSCYqWO1rS1tQQ7nrZZTTtznTqbM9j1bGts" +
                    "YFee9/ZZOtrvdOVFJKVmnELDRuMhMXBTnyHNvihhILwM2jByoMsPSIrISAytUZm5EP0GQq9HrZ2M0ftGdKQfbs3PEa0z0dEx2L0eQDW8hdH6OXblz9BK" +
                    "+fozuj3YCwQZpWlxMiAgKv24gZZ+mmWRmQRh0JxJ46AIuzfmONMCjSdMVGoJlhXHrPkXGgOz17PoFSfj6aW4EjDHYvS4CgrOxWudj4j0RGcWYCROCRsd" +
                    "5loSjeHvHCIjof4+lKrFMCroHi0FKVElOQJqu5nSCbIompqa8gp/dgTwU6kUAAUFBXlBw+15QHPn76jgaHuopUgkQjwep7W1NU8XZUc8s3Q6He6AcvvE" +
                    "bqvHuDvH9s6/u9a7q+cNahZ25vNfxbnYlU1xtneY7aFVI4qiYAvKoyVETBtXZzAwkMllGIm9snBnZL17nfdZZBlS9kCl5yKj30Dae6KifdCLjsPoczOi" +
                    "/Gachoewuv0MvfE3pL2VRPs+jdsyF6KjMFQxiM0etBZZ3ltAWrk0ew4ITTQ0DP68CdPFkiZNbitJlaZIxH0NnJz3KDSmlng0k276X6LdvktqxZlYlT9E" +
                    "9nsQvfIaZLdLEIUnIL0WMhtuRtmVmNFvIdI1yPIRHd0mhOI9dj+/X25mGcQqqIjE/J1PIhrGl7PadLsN9gPwClIYA22W7QV7x3HYtGlTSHsEALk9D1xQ" +
                    "kJJOp0OaIpVKkUqltrsuwHEcCgoKSCQSbNy4kaampm0W3erou5qamkLATyQSX9rOZEeCywG1tKvBqa0s71eVebWl3Uewprb56rtrJ9kZYOeuc1viPLuS" +
                    "4tuammiunEnHaZlFUTA1vaIVvm+tDJTM4LmrMCNn+r6zkPnBVQDtIYSJjgxFpT/3RYWdZlT5GRh2b9IbfoMhDSJeA+kNP8GwxhLp+zay5T0ydfdg9n4G" +
                    "vy+szDW76GyZbauXpMlNg1B+0FaD9DSYUCAVtmHQ7CRJOkmK7DhKKMxAv1+C0B5KK4zoKNyaO3GkJNL3Xtzl56Fb/4WwHHTyX6i6Z9DJ2RiFJ2KV/gSV" +
                    "+ggpYmDv4TdCEW0oJ+EXfSGKEUYZIj0fYhMoi0X89xbE/LRWHXS93X0j2PIed9xxjBw5cqfSMoPikOOOO47hw4fvUAA4KHZSSnHwwQfz2muvbTE7o7Mi" +
                    "mKDgyPM8xo8fzyuvvLLNx9YW6JRSoRzEl9XUPBg745HuStAIisZ25+6kLVgGXPTu3jFt69p3xzq3dbcQdlvrCPCN4ggg6JHtdIUwEc4yXFwsu2+Oe6rz" +
                    "6Y2g+Xl8NE79w6ActBlDrfkrVtGB2ANnoGufJF1/F7LoFIyyW6F1Cum1P8Tq8SufGsk0oyNF7V1gAa1OkpSXwjQgYurNU2tBVCps06PZydCYbqFbpNw3" +
                    "FhggMwglEZg4yanI6Hhk1Q9hydG43X+KMXAq3poL0OpzRJOHERsDfV5FxQeBZ6NSH6MivTCyonBg5e9stJ8xJAEvMgydng9AebQnkgi62K8G9tTuD2wG" +
                    "N2Xv3r3p3bv3LvnO7t2771B+edsbt7KycrvzkDsaPXv23OFq245GsAPZleAghCCRSIRl+NsL0Bs2bGDq1Kk75YW3bVQej8cZPHgw3bt3p7Cw8CvxpoMg" +
                    "+RdffBGKpeVq1RcXF7PPPvtQXFy822iQ3LUGssmLFi3K08BRSnHAAQfQs2fPLcbG3nzzTZqbm3d69xTQlePGjWPgwIGdzmmaJu+++y4bN270M9igA1Kn" +
                    "NApC0Serg68l4MzFlJUI2dNfqMj9RM7vGkR0L0zvbrS3Am0Nwi49lfT6MzAbXsfodh0Y9YjmxXi1v4LquxDdbsUo+BaZTb/DKzwYyYT2nj7QnGkl6aaw" +
                    "hcbOev1aClCaiOERsTNsak1Tn2khl2oXWvqSy0JiOstQzbMwul2F7nYV7rrLkT1/iSj8JuhjMSpuxaMJnfwIvfE1RPnVkJyLERnZYVqlCGSgyWYpRceh" +
                    "6x4ENGWRMuKGRWuBgbAkOqOy2UO7D/gbG/201erqaubOnRt6sTsTvN1S2fm2AH00GmXcuHFhsCt36xzsJIKRTCZZsWIFtbW1Ha55e9cTNGMfM2ZMO9qg" +
                    "urqaxYsXs379+l1KJwRgdt111/GTn/xkh75jzpw54Zp3NDAdGP8DDzyQCy64gEMPPTRM2/2qxw033MAnn3wS7hI9z+PII4/kr3/9K/369ftaefMnn3wy" +
                    "ixYtyosRWZbF448/vsW1NjQ00Ldv311KL/35z38OBeg6G7lV3+3y8AWgi2yQBj0T3TbjZnIJhtkXjel7uULSvm7LBzNh9sEggc4sRVqD0IkDsPq8glNz" +
                    "F2r5cdjUoVULjrMPVu9nMOMHo+rvw2t+HavykjxuXgsZtuBqcZKkXIcCS2DJzco0aEFUKGIGeK7jK2rmHJTCBJ3yj6TwSFTNMRBPIMtuwPI2oNf9xCe3" +
                    "rEF+f1p3IzoyArPy134lcXotsuTMzbUHuYAdxBuED+NGZDCeWw/UUBi1KTAjNMdsLNtAZdTmRJ3dxD/W19cDvn7K/fffz2uvvUZFRcV2q0vmej07aixy" +
                    "5ZGnTp2KaZp59EJApTQ3NzNt2jT+9a9/MWvWLFavXk0qleqwarKtUuDWgLepqYmDDjqIF154IfxMUPn59NNPc8UVV+QZp11BqSmlGDBgAFdccUVI6bTN" +
                    "qumMl83Nbd+eTlBtvb6g0ffkyZOZNGnSVimzL5OSMAyDKVOmhPMrpejRowdPP/00JSUlHVak7g7vHvxkh0DnJ7cd6KBBg0LvviOnwjAM3n777e1qiL41" +
                    "+qiyspKhQ4eGlcW591Bwn8ycOZOlS5duzmTLP6pAOE1hEacsVuLfpDrb9KRgYpZBER2AvQ+unlZYIoZnD0QkP8WKH4ny6nEa3iZafg1e1U14rTPRjfcj" +
                    "4mch4wfjbbqNTMNjRHs/gsosRuhShN0XSPvdprLf3uqm8ZQmBkSECjtqgSAqNEVSgtJ+cRZZO6EUriGRmfW4zmLMgiOQ5WehV38f3cNBVt4J2sNLvomM" +
                    "7ANGd2R8IsLuibL2x0zNxRUpDHswHgEHtpnG0mGFgOkbTHOAbzzSSymKjqXUslkf1cioidfsoKVGeF990Da4GQJvNR6Pc++993LmmWcyc+ZMysvLdyp/" +
                    "fUfXFPT2bPtgGYbB+vXreeKJJ/jHP/7B0qVLQ74/yMbZ2bWapkk6nQ69stz2cIHM7a233sozzzzD/Pnzd/pBzeVSf/GLX1BQUNCuWUWuyqFlWe2MVrC+" +
                    "wsJCotEora2t2+XlB2B//PHH89BDD1FeXh7ywEGa5FcF+J7nEYlEWLhwIfPmzctrWH/YYYdRVFREMpnc4TjTro61RKNR3nnnHTZt2tSudeTEiROxLKtd" +
                    "H+BcKigoKNvZ+yi3NWMgB9F2zuBvgSaRaZp+lXU+l6MgYqAKIhQZEaoKiwMyBZdVROyzc7zTji+AqbPdZGMDUMmP0VrjmN0w7QTOshMwEt+E4lMQicPR" +
                    "DX/BaXocTzlEez+MsCfSsvEMCotvQNMXqTXSV6H3V5FuBeViGWBl0zLJFlKZUlNkKVAODaFipkRJD0spMMtJ19yCaRZilFyHSq3AWX8ZZmoJeCuRZZdj" +
                    "xo+HzHIyLe/jpRqJle5Dxv0QwyxCyh5Zrn5Lx69AJsDqjZecTVHJRIqiCTANdCKCqk5mlUB3380bVJC6rktRUREPPfQQF198Me+9917YW/ar5EpzMwhy" +
                    "xcb++te/ct9997FmzZpQSz/XA9wVawyog1zAz6WFxo8fz/jx45k6dSrz58/f6ZzpQAph//335/TTT+8QHIKt//PPP88FF1zQznMLfi8oKCAWi9Ha2rrd" +
                    "nv0ZZ5zBY489ltcVKjjvwZq+Co/aNE3Wr1/P97//fVKpVF7G0gknnICUMpTv2N3DNE1WrFjB9ddf36GBbSt33Pa6w/Y3RN+a8xYoonbUWayjHr5ZtzSf" +
                    "v5dxC6fApNSKUxX1i66Utw7tarD7+BSGltBZ91npZ8Fb9v6kGt5EyQZMrwhReBkyOgq37j7Ehu8hVSGCdYjYeMx+b+CJBHr994l7FRAbhacdv0BKKEQ2" +
                    "2NmUbgXtYUmNJVRI/XhITDyKDQ+0R2O6KSSYFCauUJiykIg1EGftd7H6voDo/mesSB/kxhvRGrzk26TdnyOMKLL0PMzSU/CkiW75HNMcihI2UqWzef9G" +
                    "x0evPT9OEBlOJvMRMS6iwigDA0R89+rpBBc80IgJPJOKigoeffRRbr75Zp544gmi0WiYqvlVelVB4VR9fT1XXnklb7zxBoWFhVRUVORVhu5KYxR4XkOG" +
                    "DOn0/+vr65k/f/5OG8Lc+oLJkyeHXloumAfe/hNPPMG7777LBRdc0KH3BpBIJEIw3BYPPzA2hx56KA8//HAI7rnxkQDslyxZwpNPPhl6sl/WvVhbW8uU" +
                    "KVNYt25duyyXF154gRkzZux22e3A4DY0NPDmm2+ydu3aPNXP4Jx11k4y+Hx1dTVz5szZJQ5V8PnOGtoHgXDHcfj444/zPmO25d9lzMKLGJTaCYqsGJ7Q" +
                    "CFmBJIbnLse0e2bTJNtfBL8a1kYicDMLEdZgJIVokc1s0RVYFb+C7t3xVCMiM59k7d0knEWImkfxnOVEej2Iq6Wvk49A+72x8gFfgBVK2Qi/CYrwKLQU" +
                    "COW/L4utlgZPaJR2Mcuuwkm9ibfsGxh9noSi8/Ga3sUsPAptliLMfsjYSIQuwcuCuhUfj9P8T0zSIAzA7LxXrjDRKFRqCWbEbzJSFYkDEhm32fLe6KsB" +
                    "/MWLF7ejEaLRKHfccQcHHXQQkydPZtmyZRQWFmLb9ldSJBI8FPX19ZxzzjlMnTqVqqqqsPlGIIoWGKNdsZ6Aqy8qKmL48OGd8uhr164NdfZ3xgAGgHvG" +
                    "GWdw0EEHtbsGuUb4N7/5zRY9RvCLzIIium2hALTWVFRU8Mgjj4TptLnzB4bl5Zdf5txzzw1jPV/FaEtxCCF45pln+DqO3LUGwN+/f//QaWgLvrkN0evr" +
                    "63cJfx/0Dm7bmrHt87RkyRKWLl3auYcPIIsjYCtKI0VY0sL1HLRRilEwAbXmUrzICDSpDpuUBAFSgYVOTsWs+lO2CtXDMyTCqyW95jREbBwyMREpY8Sc" +
                    "hTgrD4bEt4n0egxlVGJkpQnCpE/texkNbhKUhykFhhAhckpcQFNk+OR4neun0XloTKEIGntpo5BIj7/hrjoVb/VJCKMv2izEi/ZH6DKE14RX+xTaGoyd" +
                    "OMKvHE4ci6r/Paw5AUdWYOh0e0XP8OgNhK5HZDaiq34PQEks7oc7Cs3QLO6OPJ3ggq9cuZLly5czaNAgnnnmGaSUnHLKKYCv8njAAQfwt7/9jaeeeop1" +
                    "69YRj8dDBcRd7WHn3sRBi7yf/exnIbcdrLupqYm5c+fy3HPPsWDBgp3S/8mdM51OM2LEiDxN9lxvVwgRGsjtaWTeGW+fSCS47bbbOuygFQDw888/z/Ll" +
                    "yzn66KM7BJDcVMCioqIO39ORsXEch5tuuonevXt3GjdYu3ZtCPaBsf8qePy28wQ7r6+LIF9naw3Ae/z48WFzmo74e9jc0GVX8fcjR46ke/fuHSYnBBTP" +
                    "J5980u7ebcPhAyUxkILusVJAIJVHxgArfhi6+rcI9zPfy+0EsgxACfCEhTBjQR0qUilE7CDs7reSbnwZGp5EKAn2BIQSUHoc2qhEKzfsC0ubfURL2i/l" +
                    "j0iFLVXQltZfixSUmP5B1SUb21szLREolDkcMWAqcvkJ6PRshFeIt+J8sCy0OR5ZfCpWwQFobWYbKAoQheiWf2Ni+pFg2fGDLxAonSFTcAKW9AORFbHu" +
                    "/iKLY6FZ2F0eftBcYtasWQwaNIiSkhJOP/10nnvuOc4880wOOeQQKioquP766zn33HN59tlnefHFF1m4cGHYzCKQst1VD2OwLV6xYgXRaJTBgwd3CAD7" +
                    "7bcf559/PmeccQYzZ84M00l39OEJAD+392wAgrmpoAGds729bTvy7n/4wx8yaNCgDoEhENr69a9/jRAiTJ/d0o4oAPytAYTjOPTu3btTiijIiPrnP/9J" +
                    "fX09lmWFGvxf9cgtgvsqK1Z3ZmxpNxYcSwD4u5q/d103j5bLHQF/n/usti8hLLLBMOib8HPwPWkR1R7a3gNrwBQE2aYfHQYffQpGarBx0WYf/yCFhRQO" +
                    "npCQmEQsMQl0xletxEIm3yK9+scYfXpj2Pu0ESfb/FuTkwQhiEkXK0vzbNYpEJSaKRBF1KYa/RRJLdBS+PIKYjPQyuQcMtLDHDAFRCWWagRZgrB64BLx" +
                    "PXuhs2AfI1L1d4SbQVsZNBEkHu2KzkLBCU1UJcJAc7dICUgPrygSppDuLiI/t83dt7/9bSZMmMCwYcOYOnUq06ZNo3fv3txzzz2MHj2anj17csUVV/C9" +
                    "732Pzz//nHfffZfp06ezYsUKmpqadskDFwDrhg0bOPbYY9vxm4ExKCwsJBaL8cADD3Dttddy+OGHU1RUhGEYoezBjlb4Hn744R16yfPmzWPAgAGhrO7O" +
                    "FDcppejVqxfXXntthwG2wAA88cQToYRx0ExjS7u1bfHwg/lPPPFECgsLO6SSgvH+++/v1kY2QYxjW+6tgP7andk7wS6wbTvJtrRgY2Nj2F1uV623M/4+" +
                    "99wEqp+5z1T7K18SBSHoHujgZ4kaJYuQYmR7t7ujmywXArWDKy2kNjG0RpNBaQOEjdAatIuIHYZZchJe62foyD5I3XGMoCndCtIgKjOYOQCus35+iemC" +
                    "IWlIN5PBI4JA6YB8yqZxqlaa654gWnYdpr0PbrYjlvJtAqZy/Ta52k89VRKk1R9t++8x9JaPP2gpoJVPSZTFC3yp0YS5m/NzNl/4t99+G8dxKC0tZfz4" +
                    "8axcuZLy8nIWLlzIG2+8wbhx48Lc53Q6zT//+U8SiQSjR4+msLCQOXPmhHzkrhptAU5KSUNDA0cccQT3338/Bx98MH//+9+5+eab+dGPfoTnecyfP5+P" +
                    "P/44bFaxrd5+oPQ5dOhQxo8fn5f3LoSgqamJI444AtM0w7THnaFzPM/jlltuoaysLMyKaeutp9NpfvnLX+ZRNlsD/KDF5LZQeYceemin3xtots+YMeNL" +
                    "o+22lfaKx+P8+te/7rDlYnCdVq9ezc9+9rPdapwCQ9q7d++wK1pn/P3cuXPZsGHDLuHvg/TQvffeu0MjEzgUq1ev7tBZaZelo0uiIEVYdOUfg5kVIctJ" +
                    "IG/v3IbOtit8YBQakBKpddabFwgts9/pH7gUBmiwKn6K1pns4tqmGPn/rncaCHLuhcimZEqJyKZLFpkeYFCfSeKqDBEjhlRBVbBfwKWFQazqNqRZidYu" +
                    "pvYfdOkvFi0NQPm7EUBkqR20wtAy2zJA529wcs6DyEo9KOlvmysSZZjE0QUppBSo3SicFjxACxYsYM6cOYwbN47DDz+c559/Htd1KS4u5v333w+9QK01" +
                    "hYWFzJgxg9dff53y8vK8TkC7+gFqe3NHo1HWrVvHU089RSaTYdy4cSQSCW655ZbwfW+//TY//vGPqampIRqNbnMrwmQyyQknnJDHvQa66O+9915Yr7Cz" +
                    "xxTkS3/nO9/psItTQKc8/fTTLFiwgEgkss1SDoFs89aMTSwWC9sedsb3Ll26lEWLFu1SL3RHztXw4cO5/PLLt/jeN954I6/94JcR69rW3fJee+0VxpQ6" +
                    "8vABZsyYscNCeR0ZmaFDh4YVu50B/qxZs2htbW0Xe8qN3PgfiJuY2qAyWpqHZiLw3UUH5Hre3zTCUygtfO9YgZFtLKtkrp0QHZwcu11sQKMR2Wz8xkwS" +
                    "EMQMH6B1zrrRmrgFCEVDqpVWN0XCjqEDaia0TDaG2Q2tfekGv4gs5x1aoxUgvKxKp8waHCNQjti8dtGO0cr+1EGXAMrjJcSlTXPMRJq+vMLudPUDPvnF" +
                    "F19k/PjxHHjggfTt25dNmzYRi8X47LPPmDVrFhMmTMBxHGzb5oc//CEzZ86kqKgI13W/FA+w7YMghCAWi7Fo0SIuu+wyDMPg7rvv5rHHHsM0zRDELrnk" +
                    "Ev72t7+Fee1bqz4Nipp69OgRVpi2VYh89tlnw85ZO1OMFoDC5MmTw6Kc3LkCjzWTyTB58uTtjosEtQlb85qHDx9O3759OwwW5zb5Dnrg7g5+PDj2/fff" +
                    "H8/zwmBj7gj46ueff96v79kC7fVVrfeggw7KO48dvefdd9/dJesNrsvEiRNDA9lZkLgj/j4EfBHgpiXxCgyKhE15tHBb2Js8KkNrBUiMrBdj5NgS5XnZ" +
                    "ylixJTKk/V+FrzKZ8jwa0wHg+xy6r7jgmwS0osBWCOnS4qRoTLdSaZdm/7eNcVE6m2K5eU6VbUxiSKNdEbFSyv8eITrJTmp7FDpL/QhKrDhFRoTGmMjq" +
                    "6ezeYFMA1s888ww33ngj5eXlHHPMMfz5z38mkUiQTqd5/vnnmTBhQgieBx10EKNGjWLOnDlfmlRw26BdS0sLmUyGiooK9tlnH0aPHk2/fv3o168flZWV" +
                    "RCIRMpkMra2tjB07lnPOOYc//elPYcXwlgxeXV0d559/flgKH3CehmGwbNkyHnvssVBbfUfBPjAWxx13HEcddVSH3Hkw52OPPcbnn3++3WC7NUon8Aj3" +
                    "33//PMmIbQ3wfZUjOM8HHHBA6Am3XWsAqMuXL6esrGyXG6fgvkun01t1aoL1Bvn3HeXCG4ZBMplk5cqVlJWV7TSlE9xThx566Bbvb4Dp06d36EiZiM19" +
                    "OWQiihc3KLRilMUDwO8coAXaL1rSYEgDmaUx5tcu56HPprCpaR2nDDuSQ/uPJ5G9eJ5yfUVNYWxOUBQaoTuuQQ3W6yiP5oyfpWNLwvx7I9Dd0ZqIAaYh" +
                    "SKoMjalWKPTVjGWHnri/6/C0P7+ZvZma3CSvLvkP/1jyPsOr+nLukMPZo6QPQRjA1R5SCCQyq9VP/q5E6Oyi/UkTVpSE6fcXkBEDr8XZ7YBvGAYLFy7k" +
                    "X//6F8cffzynnnoqjz/+OOl0moKCAt544w2uuOIKunfvHlIOl156KRdddNGXCghBpkpDQwOjR4/mtNNO44QTTshT0Kyvr6e2tjZcV2trKyUlJZxzzjk8" +
                    "9NBDWwT7XO++oypW8JUxDzvsMKZPn05zc/MOG60gKDx58uROASGgloLMnNyHc1vOcwD4nRml4O9BFklnABHw920DfF+lt+x5HtFolIkTJ3ZIVeTuWJ57" +
                    "7rkvhXbKZDLst99+LF26dIvgnKtlM2rUqE4pSfBF1d5+++1dSoEWFBSEBqWje2pLRV4mWqKlQmuwEhZe1KAsWkxRJEGeKGawrRIKqX3NGk942NLGBDLa" +
                    "5Y0VH/H32a/w7qpPafCaAM1ji95mVMVAvj3km5w59FD6F3UPXWHX80XYpPCB0kPQ1oeWWUW3lJehTifBVHzREsHxTAzhghIoqTAkbGgxcEQU4SZpyDTn" +
                    "7RI0/k5BaI2HwkVhCzML9ILP6lfy6Gf/5PnF77G4bqWfevkF3Dnrfzmq93jOG3M0h/cch234hstxXZTUmEL65wNfc983kUZI28StKEVW3K8AK4wialNb" +
                    "2M98teOee+7hhBNOYPjw4Rx55JG88MILVFRUsG7dOp588kmuvvrqELyOOeYYDj30UN555x2Ki4u/lEYOQcOR6667jgsvvBCAZcuW8dxzzzF9+nS++OIL" +
                    "amtrSaVS4c0di8XYY4892H///SkrK6O6urpT7RXDMKivr+eaa64JjVnw0AQP5LBhw/jnP//J6aefztNPP91hs/Bt5aMvvPBCRo4c2eHWOzBYjz32GEuW" +
                    "LGmXGrolHj8Aky0BfgBKkUiECRMmbJHvXbFiBZ999tlu4+8DIB8yZEin3HTwPiHEVqmsHR0LFixgxYoV4T2/LbGZ0tLSDumcXK98a7GWXe3MBUkVHQW1" +
                    "zZxII7LABFNQFSkhatgordtJpFkeYAgwDAwM1jXX8swX7/HMZ1P4T/VcEB7YMUwrkU2HdPm0cRGf/mcBf5r1NEcN2I/T9zycb/YcjWVaIfh7AoTydw3t" +
                    "SlmF3/wk6STBsFnYAtUpRY+owPOyDIww+LzBAGXgaIeGsNo2q4+igwbmEkP4zdfTTpI3183iyXlv8ObK2dRlNoFpIuLxkHpq8Fp5ZvGbPLvsPSZWDuLU" +
                    "oYfw7T2OoE9RThaT9jOAsqGKvL9b0qA8WgSGRBRGsxmku0kyMwdshBBMmTKFGTNmMHHiRC655BLefPNNMpkMhYWFPPbYY5x99tlUVlaGgHXjjTfy4Ycf" +
                    "4rruDis1bgnsq6qq+Otf/8qYMWOYPXs2v//97/nPf/5DU1MThmGENQCBqJjWmkwmw6effspHH31EYWFhp4E8wzBobGxkwoQJnH/++R0+pMFDvmjRIl59" +
                    "9dUdyr0Pzkt5eTk333xzh9IAuTURv/3tb9tlogC0tLRsM4ffGeBrrRk2bBj9+/fvkL8PPvfJJ5+EWja7g78PvOm999473OV1Rj19GZIfjuMQi8V45ZVX" +
                    "QkO8tZ0iwH777dcpfx+c312dOrqlGphgnoDOCeJ1eYDvBz+lT66U2mAoesXKfT9be+3A1xUGLakWpm+YzWOL3uKNxR9Q7daDtHyQF6A9P6vFMQBsTBHB" +
                    "tD3Wew08/PlLPDz/NYYV9ee0kYdwcv8DGFo+AFtaoTeeN6PygwFN6WbSbgYhDTZ4Uea0FNEjVo2SvvOMtpjVUgBYaJLUZpo2s+pZsFdA2kszt3ox//ji" +
                    "A56f/w6Lm1eAbYIRwYyWoNF4arNgm5AGRsJXpJuxYQEz1s/jpml/53/67ccpww9nv16jqIoUQqC0qDaDvvLbr1AVKQGBb1C/JiMAs1/96le8+OKLjBgx" +
                    "glNPPZUHHniAqqoq1qxZw913381tt90W3tR77rkn1157LTfffDMVFRW7pFtTrid63333MWbMGO666y7+8Ic/kMlkKCgooLy8PC9dMFfNUUpJIpHYYupk" +
                    "7hy/+MUvwsycjgDQMAxuv/12mpubd9i7d12XG2+8kW7dunUIXgGoPPLIIyxevLjDB3NbAoZFRUWdpiYGILrffvt1CqLB5957773dyt8HI6BztgZ4u3qY" +
                    "pkl1dTX33HPPVr37XMfggAMO6PS8BUbgy1jvlu492HKRlylyQFYX2mBAj4Svmuhpjcw7GJ8OeXnRNF5Y/A6NpOlf2Z/GmqV4hkZ7Gq00SuqwvkgqF4XA" +
                    "kRqTKNgRKiPF9C/rxvSlc1i9fg3njT2OA/uM9bNjjHwuP/jZmGnBUR6WhIyneL+hnKMqqvGExtLQ4koWN1sgDfA86lKbm6DI4OANwYzl8/n73BdZnaln" +
                    "cK/exJtizKtbjTYMPKX8to0i/6HwPL+5iRGNYmnBhKpBbMw08uDMl1lTvZoLx51MxIyQTe9hs3SyPyrjpX7AujDytQH8APBefvll3n77bQ477DB+8IMf" +
                    "8Oabb1JXV0dZWRmPP/44kyZNYsyYMWGruYsuuogPP/yQV199lbKysp0GfSkl9fX1/PCHP2Ts2LHce++93HbbbVRUVIQibluaY1ukfA3DoLq6msmTJzNm" +
                    "zBgymQy2bec9FAEgzp8/nyeeeGK7QTiYx/M8hg4dyiWXXLJFo9LS0tIhd789I5FIYNs26XS63fcEvx988MFbBc9dVQW6M/eiYRghgHamDZNMJvnPf/6z" +
                    "y9YZ7Ho2bdrEHXfcwfLly7caWA0MQnFx8Va1bOrq6vj44493iSHNDWoHfak7Opbm5uawyKuj4zA1vgCmAnRBBJBhSqbONgFXaDw0QitsDM4edSRnjz4S" +
                    "gDfXfcIJT12PVwRCic2Uhg6da3S2SYgUoFItjOkxhtdPur3NVffjA2gVpm4KAUpoDAwaMylcV2FHJCjJnLoICIGhDDBc1qSibGiJgK0hJUOJZI3Ew++L" +
                    "q5XigL4jOWSPceG0v5/7D66ZcgdmoggXjSfbE+wajRKglUfUi3Df4T9hWGG3zbsez8NVHlLoMH1VsPkB7JEoBcNAFX19AD/35v3Rj37E9OnT6dmzJz/+" +
                    "8Y/54Q9/SDwep6WlhZtvvpnnnnsuL4f4jjvuYMWKFSxYsCBM1dyZh72wsJAzzjiDTZs28cc//pHS0tJd1iPUNE02bdrEeeedx/nnn99OITI4D8HfJk+e" +
                    "HGrR78j8Wmt+9atfhcaqo6pa0zR58MEHWb58+Q7tIgIACRQz2/L9uTuarak4rl69OuTvd0fANgDYbREgmzFjRlgZ/WWuZVt2xqNGjaKqqqrDwH9wjV98" +
                    "8UXOP//8Xba+8vJyli1blnf92l7Pzz//PFT07HDnp3Mte0kMpKK8oAilfe7JFb7lsKSBaVgIQ7I6VcM/ln3AT6Y9xK3/fhg3IpCe8DVx9GawD73cbNRU" +
                    "Kw3RCDM2LeG8l3/FL2c8xitfTGdx03paDAfLkFjSxJAmUpp4wsRVAk8rmtNJEA7KMEBY9LObQYInNWBQJNNYEZVNGXKoyzThaYVQCkNKTGlgSRPLtFnd" +
                    "vJ5nl7zPtf++nz/PeQUiMT9bZwvRVP+/Ja3S4eIpv+Xn0x/h1RUfsCG5CdMwsAwTQ1pIaaGFgatVeA67xbIefgD4+usB+IFnNXPmTP74xz8CcMopp/A/" +
                    "//M/VFdXU1payowZM/j9738feoNaa0pKSrj//vvp1asXTU1NO9zkO8iaqaqqYsCAAXz00Ue7VLjLNE1qamo46qij+OUvfxkCcPAwBHzwcccdx1133cVH" +
                    "H33EU089FVIg2zuX53kceuih/M///E+HeeSBF9bS0sLvfve7na4UTSQSHXp7ARAMHTq0U/4+OL+ffPIJLS0tuzQmsyM0xN577x02oe8I8ANuWkpJJBLJ" +
                    "6/q1s69A/39bm4HDZi2bLTkF06ZNQ0oZFiru6CvoV3DggQd2WIGce46CbKvOqCRTaz850hDglUWRXpx+iR5IIYlaNh6wqmkTc+sW88HKuXywcRGL6laz" +
                    "qmUj6FYw4wgrAkojhMoGUMnhz0WOn6wxtKQ+U88jy16FJRLMCOXRYvonKhlS2pu9qoYwqttABhZ3p0+inJjpi5ClXQ9cA0NLXJWme6EJWvvKmkqQsCWJ" +
                    "iIC0AmXR6jh+cNaSrE828lnNEt5d/SkfrP+MebUrWd+8Ebwk2FGEGQWt8gLUuQVWuk0A+d+rZvDvFdPBTNA7Xs6wkh7sXTWUA3rtyciK/vQt6IklzTCf" +
                    "v5tdCsJAF5qbax6+JiPwnm655RaOPfZY9txzT37+858zd+5c1qxZQ0VFBXfffTdjx47l6KOPDqmd/v378/DDD3PeeeexevXqsBXdjgaigA6piR0dlmVR" +
                    "U1PD/vvvz5///OfQiAQPSlDEc9ttt/Haa6/x2muvhSqd27uG4P2maXL77bd3WNGae67//ve/s3z58h0OkgbfHYvFOtTE3x7+/v33399m7/bLHEEB05Ya" +
                    "iEybNg2l1JdW/Lc91EqQ6rolqYoPP/xwl6w3uDbb0vAkuJ6dOiYymzAiLImbMCi2LRyZ5u21c/j38o95f8N85lWvZGO6FlTaR7xIFDMWxdAJPFyUp1FC" +
                    "5Hn22aXm/EGCEHhCYEgDUWARzUgy0qPWq6OmtoZPNs3nySVvgrQps4oYUFzFiLI+HNRjLz6qXwy2h9AKpMmHtXHYI9vv1nb5rD7CmnoLETHQSrK4eTkP" +
                    "zn2VV1Z8yIzqBaxtrkW7Sb8azIwQtS08I4rrSbRS2faOWf492yA992fAzEtAxuN+UNtVrE6vZ/WalUxZ+SF8YlMeL2FUWU/27r4n+3Xfk316jKCsqIio" +
                    "YeHFDYQh8htn7eYR3MCtra1ccMEF/Pvf/6aiooK77rqL0047Ddd1icfjXH311fTu3ZuRI0fiui6u6zJ06FCeeuopLrroIubOnUt5eflOZVHsCrAPStg3" +
                    "bdrEEUccwV/+8hcKCgryHpKA1pkyZQq33norkUgE13XDvPvtXUOwzT/vvPOYMGFCpxWQgZBWYBR2RYvGjoqvgu/9xje+sVUj21mBzlcdSwoyXjoTIGtu" +
                    "buaTTz7ZrWsNqLJEIsG4ceM6pZ+klCxfvpyFCxfukvUGxiIA/M6yvjKZTLuGJ+09/Cy+6YTESERp9RzOfP1X1LY0o1QAkDbCsjBEBIGD48RxvQyuAKSD" +
                    "YWiKRIaYCREpiAoPS3i+fyzA0wJHW2Q8SYuCZk+STpo0Z2kSZAxMkLbCEh4aSZ3XQO2mOj5Z/xmPfPY22DbSMnGyUjufNsRZnS6gl9EEQvL6xiJadIyI" +
                    "SpK24kzdsICpyz/1g7iWxozauLoMHAUupEgiPY+EKSgyMsSkhyEUWkg8Lcl4kqQWtChJyrWzwUHw9wHZalzh00tGNIoQGuV51LgtvLtuNu+umgXCpiJe" +
                    "Qo+icn9/UxBB2yYkXXa7kloHnOOMGTO45ppruPvuuxk3bhy//e1vueyyyyguLiaZTHLhhRfy9NNP069fvzxP/7nnnuPaa6/lpZdeori4OKQ3vuoRFBFV" +
                    "V1dz1llnMXny5NCzD4Ak+H3ZsmWceOKJKKVwHCf0/ncE7LXWFBcXh1r3HXl9wTm+//77WbNmDZZlbXFHFLQu7Cy/O5gnaIKSO2cAomPGjNkif79hw4Zd" +
                    "1oVpZwx8VVUVgwYN6hTMAm56/fr125RF82XST4HeT+/evbea6ppOp3c61TU43vLyckaPHr3F67lkyRKWL1++RSNjhvpfiQhG3CbjeVR7jRAxMESBj0lK" +
                    "I7T2i5Xw+G7fBQxPJCmKanrLJEU2FJgeUUMQNTxsobCkh5C+VLJSkBGCjKdJupKmjGRdKs7yZAFftFosb5V80RpjaaqAZEaCtMCKYUYUUmSrYT0H4Rl4" +
                    "QiAMyZpWyce1EXp3byDjRZiyqRSMCK5MI7TCdCUiEcOTEi8tcVvTlEdqGVXczJ4lLiOKUgyNNVIRcYlbgqh0sLKyx0oJHGXQok1aPEldxqQ6Y7EmHWdD" +
                    "0mRjRlCdlGxIR9jo2Kx3oyhhoGUEW3hoM4ayDRSKareR6g01EI1iRy2EZaCTDl+3EVAc99xzDyNHjuTiiy/m5JNPpq6ujuuvv57KykrWrVvHOeecw+OP" +
                    "P06fPn1CPfri4mLuv/9+xo8fz5133klDQ0OYMvhVAH/g1Tc1NYU0zfe///0wlbOtVg5AaWkp5557Lg899FAIvDviiQWUyVVXXUXfvn3bqWG29e7/8Ic/" +
                    "bBNobe28BfRQ2yKkXNXJzvTyg89+/PHHNDU17TY6J1hrWVlZp5IdwfV7++23Q9psV6QD7+h6A0+7M6mKrWnZ7KiRGTlyJGVlZZ3Wj0gp+eijj7ZaR2DK" +
                    "QBq40AZb+OAufX7c0zk6ykJDWnJyz438bdxqcFJ+GqJpg3J8dzXgPXIzdVT2DzKrcSAE4IFo2OzluhZ1boIFTTYza+NMrSllRo3NinQCsMA2sx2uPL+7" +
                    "lbRxdYx/Vpdxcp9q5tQk+LQpgZBplLIROGjbwUlHECrNwRWNTOpZw8EVzQyLtxAVrflr1G3WTZvfddu/C5SyafAiNCu4et4Qnltdhm2ncLB8akh52Uwj" +
                    "ExE1UWh0RCIjoj3z9TUCfcMw+MEPfkDv3r05/vjjOf/880kmk/z85z+ntLSUFStWcPrpp/O3v/2N4cOH4zhO6GFcfPHFHHrooUyePJkpU6YAfhl4AHC7" +
                    "eise6O44jkNdXR3jxo3jtttuC2mVIOjVkcdUUlLCvffey4wZM5g9e/YOeWLBw9i/f3+uvvrqTvvPBn8PvPttAa2tAUVwLgPA78gz3prkwsaNG8OeBF/V" +
                    "jqyjebYkKRFcv/feey8Mrn5Vue1t15qbGrk1qmxXNSwP7rEg22pLVb3BnFukAYPzK4qiaBNI62zP2nyQU0hM6fH9gY1AK4gICkFr2qA2E2OdY7MxI2lI" +
                    "CurTknrPpsWBpJK42sgGRSWGdLBMQbGh6W4lKU9oesZdBkVT7FfSwn5VtfxAraS22eKV6u48uaKMqTUJWilGWg4mHp6SYMD79SVoZfOvjcW0ZAxkRCBI" +
                    "47kSlTE5uHwTVw9cw5FVtcSsdDb3FJAmjmvQ4Jk0OAYNjkGTa5BSBq4SCCRIj4jUxKQgZjgUmooS0yUhXWKGhxQOpUaa0hj8bNAS/rluFM06gcSvO9hs" +
                    "RzRaC4TWaFv6rQ43BZbj6wX7gUcshODUU0/ltdde45BDDuHSSy+lsLCQG2+8kXg8zrp16zjttNP43e9+xxFHHBF6h0EO+oMPPsi//vUv/va3vzFjxgzS" +
                    "6TTxeBzbtkOvbmua71t7CAKVyfr6eqqqqrjsssv4/ve/TzQaxXEcTNMMvz9YX0C/BA/hOeecw6effrpTwVOlFLfeeiuFhYVb5O7r6ur4/e9/v1Odszoa" +
                    "paWlHW7vW1tb2bhxI7179+4QlLTWHHPMMQwZMiSURd5d8aN169ZRW1tLZWVl3n2Re+7eeeedkEbcnfz9tkhVrFmzZpelum6tYXnu9eyo4Ul7wEf6HndR" +
                    "FG0YoJ0871aiUdJGuylKYrDCKeQvC3ryeVOcjY6JcgQKiW1oiiyPAltRaHmURjx6JDQRMr53LiRKa5SCJtdgo2szMx2loV7QnBa4SmBLQb9EigmVaQ4u" +
                    "b+bcAWs5d0ANH66P8IdlPXlxbTFJoxjL8CuAlzWZfNRcxn9qCkDYmNIgk4FKo5mfDlnH9watxY6mIBNhaXMJM5sKmVNnsqghSl3apNUzAQ9DKiwpsIUi" +
                    "YggsAOnTWDq7yzG0wJAa0/CICUVJVNArkqRvNM3AMslB3ZO8ur4I2/A9WZ2T4SN1VuDBtNAFMTRNX0e8zwOMZDLJySefzDPPPMORRx7JOeecQ0VFBddc" +
                    "cw3pdJpMJsOFF17IFVdcwZVXXhl62sFNecQRR3DEEUcwffp0XnjhBaZOncrq1atD6ih45ba029LDFry01iSTSZLJJFVVVZx++ulcdNFFoQZL2zz73G13" +
                    "4FWbpskll1zCY489tsNgH3xun3324eyzz+4Q7HO5+z//+c+sXbt2l1MSHQVtg6Kxf/3rX4wfP75dPUBwHrt3787777/P22+/TSqV2uU9DtreU5lMhhtu" +
                    "uIFNmzaFawga3XzwwQeccMIJne6S/vKXv+wSimRb11pfX8/1118fNsAJ1jt48GD69+/f6a4KYNasWTQ3N++ShidBP4Px48dvMUi8cuXKberOZgY5gqIo" +
                    "4mfa5LUW1GjhNyE3UGjX5bHPHXoXFjOyKM3hRfUMjLVSFfUoM1wMmck2sNX5tEhHTVOCLoGeScozWe1EWdxqMqfW4vmVZdy5sBuDYvWc1LuBb/Vp5cke" +
                    "i5i6ppgfzhvA7IY4Ml5ASybBw8vKmdNagbQiZJLNHFDQwL37rmBkZTX1tYU8tKI7b6wxWdxSgGUZDC5oZXRxkqGFigGxZsqjirjpkjA8osLBICsnkc06" +
                    "0lqTURZJz6LVFTQ7mo1OhI0pg1WpGO/Uxnlxg6S2OQ2k8M9UfvWWDvQ0BcjCCB5f7xHcRA0NDZx88sk8+uijTJo0iWOOOYa+ffty9dVXM2fOHEpLS7nj" +
                    "jjuYPn06t9xyCyNGjAhBLqCH9t13X/bdd9+w6vDDDz9kzpw5rF69mvr6ehobG/N0Y9pu2YN8+cDI2LbN4MGDOe6445g0aVIe0Od+NnjQTNPkpZdewnVd" +
                    "vvWtbwFw2WWXce+99241cLotY/LkyZ3m7QeAVlNTw5/+9KddGnDcUterYI6//vWvXH755WFjmI5Av7KyktNOO+0rua+qq6u5/PLL80Ar+P13v/sdJ554" +
                    "Yt7ur2285ascc+fOJZlMhqAdJARMnDgxNKid8fe7qmF5bj+DXr16bbGfwaxZs0gmk1t1YEwvaN5UbLezDIHGjqEyuDrKT4cv43u9NxI1kmAETY79DlEo" +
                    "QbNnU98SoT4jaHAtmj2LTJbOMbXClBA3HIpsj2LDodzySJgO0UiaQbEMgwo0x3Q3wN3IF60J3thYyRMru3HHFyYn92rkysE1fPyN+Vw0dyAPLXcwrTiP" +
                    "rarE1RKVSXNq92oe3P8LHMfg5x8M4InqCrpbKb5RleRHI9exV7yBaDQDptpM7+DHLfxSWgNP2XhaAArhW0QiMkMkmqHEF9ZkiGzxc1mzBsvzDGY1FnLc" +
                    "h2VsciNInOyn2ZzlmZVhpsjmv2EEN1IymeSUU07hjjvu4JprrmHEiBE8++yz3H777Tz66KPEYjE+/PBDTjnlFM4991y+973vUV5eHnrUwU1aWloaev0A" +
                    "zc3NbNy4kerqalIpX0HUcRyam5sxDCOMDViWRVFREUOGDGHChAkcdthhTJw4kUgkEgJ9ELRtC7QBkFx77bVEIhGmTZvG008/zT333INpmjtVN+B5Hqec" +
                    "cgqHHHJIaNzactGBd3/fffexYcOG7fbutyYbERTBdRYzWLZsGVdddRV//etfw3zwoPAs971fZm/YwLO3LIuXX36ZZDKZdx6CczR16lR+//vfc/XVV4f/" +
                    "l7vWrypQ6zgOkUiEp59+Okx3zD0/BxxwQPh723O2qxuWBwYjoJA6owy11mGQeGuUoakDRcySmA98Ij9yaWgHlyi9rEa+23MF0UgSMgYrWkuZ0xRhdr3N" +
                    "Z40JlqWLqEsKP+VSSTRg4WEIByk3e/eGlghhYBgeccOhMCroHnUZHmtgVGELw0o1w6ItDEw08oMhjfxgoMF7G8u4f3E5+68YxFUjNvDgvosYWdiba+f2" +
                    "pdGwwYUf9F/J3fus5NnlpUyeU8rgMsm9Y5fzjco6MD3wNJ5rs7ilmC+aLZY1xViVtNmUFtR4ERxX42qJq8DLNlHXWiKRWEIjDU3c8CgzPXpEUvQvcBmW" +
                    "aGFgoUOV2cre3eu5fsAyrp4/GKKWn5qUe/HwG6Prr1m17dZAP6Bcrr32WubOncsf//hHiouL+eUvf8lhhx3G5MmT+eyzz5BScs899/DSSy9x1llnccop" +
                    "p9C9e/c8aiX4PtM0KSgooKCggD322COPjz7ooIPo2bMnFRUV9O3bl4EDB4ayuR2lHnb2AMyfP58bbriBV155BdM0yWQyHHjggTutChl46fF4nF/96leh" +
                    "AeisAKempoa77rpru7n7oACsMwojmDMA/LaeZEBt3X///RQUFPDrX/86NJIdBb6/zBF4wg888ECHQBiA/o9+9COklFx55ZXtvqNt5tOXNSzLIp1O89xz" +
                    "z+XtyAIjGmTodHTNA/2cuXPn7hL+PrfIK5fWbHtuhRBhwHZrcwqB0MKSWDceSKZfDJIeWm7uWWgLRcY1Ob77Rn4ybCOvrYrwYX2cpa0RIm4rFbEIvQo8" +
                    "+sca6RF16R7JUB7RFJkuRaaDLbNbE1/ZHuV5tLiSOi9CXVqzsdVkTTLGylSEdUlJKu0Ri5oMLE5zQEkL+1c69CmsA+nyz9XduW1OGQOKPO4cX8u/Nlhc" +
                    "P6snk/o38dsxq/jtpxX/r70zD7OiOtP475xTVXfpjaa7sReghe6wtQIKRDCIDK6Piu0SDHFPojHhMTHJkBnHZ9DEPG5hYiZmZkLMRB1lGBJDmNEYY1hM" +
                    "VAiIdFxIqyigmEb2pre7VdU580dVXbqhFYgSo97vL7jP7apTt6re8533fN/7smxnMfPG7uLUwbvBFbR2VvLU9jhr9iZ5OVVEd8anRGqqEz61RT6D4j51" +
                    "ToqBMUOxpUlYhrhwMVj4Blxfk9bQ4Up2uw67Mw47cgm2Z3zaMwZtHAYWKU4p28uoasVX/1jLpp5SpPLQ2EjjAgLhC0yxhfXUdnL3rf/AJZKPFOSiZWxT" +
                    "UxMLFizIdxp2d3fz0EMP8cADD9DW1pZ/SWpqajjzzDNpbm7mxBNPPOiFjbL/6KGOxMwOp2qid+Z34OZvlAXdeOON3HXXXTiO06fs8r02PEXZ6dy5c5k/" +
                    "fz6ZTKZfaYlor+Lmm2/m9ttvP+zsPsrqmpqaaGlp6WOu3h9IPvnkk5xxxhn9XlcE5pF2+5w5c5g2bRrV1dUkEol3nVDeL9rJGENnZyff+9738vRXf6DU" +
                    "+37OmDGDa6+9lilTplBRUUEsFvurcPdaa/bu3cvcuXPzAnpRYqG1ZsSIETz//PP5+33gc2dZFsuXL+fss89+3+gc27ZpaWlh9OjRB+3FRJPQ9u3baWpq" +
                    "oqOj45DPtxBgZKlN7J9PJTVQIXJmvxqCkIEzlZeiwvE5ztrOsFLB5MocE8v2MaTEUGFnwMru1x/O8/USjAo/M3kdeEHIj8tQxlK6IeehwFN0py1au4tZ" +
                    "3V7CH3dbbO/RVBbBzMEpLh7SiWU85jw3lMZ4N+cM9/nRpmOYPLCTIU4na7aXcP1xHSQsl59uruCBzaVs7ZSUJxWTB7mcV9HO2AFd1BVrlO0BYeewscH3" +
                    "QgAOfXuNDlTlQnct0H33IjzFXq+EN9MOr3Q4PNVexLZ2h7XpJDt1CYocPjZGOAhjkL7BFCliz+0l/e9rPlSAfyDYWZbF1772NW688cY8fbNjxw4WL17M" +
                    "kiVL2LJlS/5hj8fjNDQ0MHnyZKZMmcLo0aOpqanpN9vsDRIH0iP9iZBFmW5/n0NgP7d+/fr8y/d+dfICDB8+/JCaP1JKNm3a9BfV+cfjcYYNG/autI5S" +
                    "ilQqlW+2ebesNRqD4zhUVlZSWlp61DtWo/u2a9cudu3addjiZNF3kskkFRUV/eoFHS3Q37lz50HmIdE9Lykpoa6urt9riOif9vZ2duzY8b5QOtE719DQ" +
                    "8K4y2Ol0+pDPQF/Ar0mgbjqFnBP4l+w38RBYwsfLGW4a1cYto7fhqJ4A/HyL7qzFzkyMnTnJW9k421Jx3nZj7MkKOl1Ft69IaQtPB2yRQSCNxpaauPCJ" +
                    "KUOxbaiM+QxxUtQn0gwuFgxNZKhNZMGC3ekET+0qYdlbild6Srlm+HbOPtbji+vq+c3bcVLCAS2pT3Tx3RP3MMHZxWfX1LPPj3HR0BSfru9gTEkPjnHZ" +
                    "llG83pXktS6HrSmLt3JJOjybnCvJGYFnAvkHB4+48EkoTbGtGWSnOSbuUxfLckyxpjLuUitdyu00xEKA0YIuXcIdr9Rxx8ahiLiD9DrQIoYxFsJoRFzg" +
                    "vNJJ9l9W71dS/nBhfp/SxuHDh3PTTTdx+eWX5wG8vb2dZcuWsWTJEp599tl85pPJZPIURG1tLXV1ddTW1lJeXk51dTWXXXbZO2azBwL6gXx97wwtyr7W" +
                    "r1/PDTfcwOrVq9/37sz3S+/nr7lCi1ZFH1TT0pHQaNH9/aBKMPsb69/6PT/c8QkJRjaWwT9OwXP7Gp4EWvmSItKsmLKBMuWyel8ZGzstXu0uYVNPgm1u" +
                    "kn2+xPcF6HADF0LZARmYqGCCfwtBXig/kM/cz3AbABdL+JTYmrp4irFFXUyuSDGxMsWY0hw7MpI/dFaw4NUS1rRXQyyohxG6GN/P4phurq3vZHrVHqbX" +
                    "5uhMu/xuZym/25lgbcdA/pwpJaX9MHsPl+GRyYsMKQXj5St0+vLwAoRGCpcyy6Mq7jM62U1TcY5Rpd2MKc4wrrQLY8UZv/wTtGZqUCqN0WCwAuE4y0e9" +
                    "ncO9YxUm7X8oAb+/l2LChAnccMMNXHjhhRQXF+cBeOXKldx7772sWrWKRCJBPB7PV9tElE5PTw8NDQ2sXbs2TxsdqrnGdV3WrVvHwoUL2bRpE4888kh+" +
                    "wmltbWX+/Pk8+OCD71tW/04v2OHQDO9VNOu9buy+0wrlr2l2cqRj/Fse66HuyV96re/1OTjc50wIMPa4SrhhMrlMSHHsLzAJzMFti2NjnbzZbZF27QCg" +
                    "ZaAnI6QBW2BpEEZgwvp1Hx+hBdJIjAKpDRoLLQLAVUaGkjQeRgs8qVDa4EuNr61g8vBDukcIhtspPj10D1uzcRa/MQgrrjDaC7T2pURJg+dKqmJpbhmx" +
                    "m9+2+Ty2dxieG16l5SKURBmD5Rs820IaH7TElz6KQIhNGgLtfATSCHxpENpDS4njS3whcI2LxoDrhBOGIRHTjIvt5ZS6LK09xfz6rWJE6K0abItrhASr" +
                    "y8O7fRVmbxYpRfhbfTiBP8oaI+AfOXIkV1xxBbNmzcprm2utWbp0KXfffTebN2+mvLw8/0Iopchms9TX1/P444+/68Zc9De33HILDz/8cL7mGODJJ59k" +
                    "zJgx3Hrrrdx///19dGg+SAXIQhTib24lAJjYqUMwV48nl87mk9u+b1vIyUsTqCOEUscg0F424L91mMEjQWhsK4aWEl94EE0kSgU8sDQBx5P1ggYny0Eo" +
                    "hfb8QNzMsrCVRFsaP9MTnjsOPT4oHzUgDrlA1tmTHuTcYGFhW1jCwstpUHZgRJ5LoRyF1hKTzUFRAiVi+F4G3DTYNkIWY7KpYD8BglWIFRrt2hZKJvC1" +
                    "C34qPI+DLRS+sJHCCwzZTSARjZvCsQw5eyBKdwULBRQQUBWWq/HuXI1p60FJiTAa70MK+O8E/EVFRTQ3N/P1r3+diRMnArBr1y7uuOMOFi9e3EfjJQL8" +
                    "FStW0NbWlq82uPDCC7FtO18SF1FDzc3NPPbYY/nqBK01DQ0NdHV18fbbbx8xfVCIQnycwgIQ5Um8qLO0v+WTACl8DAIdApMUEp1Nc/W4C/jM0KnkhMFy" +
                    "YuRSXSzZ/DSLX/sd2mT4ZMVY5k2YjS8F329ZwlNtLaAk48ob+M6Uz9Ht9vCNZ37MzvZtXH38+cxuPJP7XnuMhzf+hrhbxOVNl/DphpMZmihlc2YHP37p" +
                    "cR7ZtJpYPE7OyzJ+wGhumXQpcRx+8PKjPPH6UzglZeRSPYwuO4avjp/D2MrjEBpWv93CnS0/Y3fPdmY2nsr1TRfz9I6X+GHLEu6eeRPVzkAyfpC9xzQk" +
                    "VJxH33qWe1oe4tiSBm6behUD7BIe3byaBa2/RDk2xlf4UqFMBinBTxSR0wapU4BBC4f8hq/RGFshixw0PWg4yFLxwxi9pQsi+75Fixbx8MMP8+Uvf5l5" +
                    "8+ZRVVXF3XffzciRI7nttttIJBJ9uFqlFMuWLeOaa67Btm22bt1KdXV1/jsRNz9r1iwef/xxhBC4rosQIi8NEKl0FsC+EIV4F8CXZfEwwXxn9NEcXHOK" +
                    "7zOpciRnD5vE7lQXr3S8ydTGqcxsPJnt2Q6Wv7yS66efw3nDAx2IjM7y+7YW0D5ViQHMPDbQwO7xc1y79BaOr2zgrGEn8Fx7Kz9/rodvnnYt3z7pKnK4" +
                    "/GnHZs4ZOo1zhk6j+Vff5tE3nsQYl6tGTOeC4YF5gkxYrNi8DtftoiFZy//OvIcRpZVs2vdnLCH4+xMv4eTB45i+8As0llRz5rETsAT8G7/gpJpRDI9V" +
                    "k1ABrZD2PXzp83rqLUj1cN7Yk7j0EzMC6qKijsWv/ZYOP4cUNmAwQqIxCJ0LTFmEwRcxhNGYyPrQAI5ClMQjkif4XY35SDxMURNPb1Gze+65hyeeeIJ7" +
                    "772XadOmcd1111FWVsbcuXPz0r5ROI6DUgrbtuns7KSoqIiNGzeyceNGWltbeemll9iwYUPgxBZuPkZNVr0/K0QhCvEOgC8RMCA04BbiMDYSg9LFwGpc" +
                    "k8714BvNQ68v5xu/uIVfXX0v5w6bTH1JBQMSlZzfOIU/7d2KRHLmkAnUF9XwZtdraFx842G04ppR5/DzESv5c/cefKNJZbuJJ8u59PizMMbwhd98l4Uv" +
                    "LuayE2Yzc+gn6SGLwVCWGEhz4zQ2d28nl84yddBxHF9+LC07n+XCky5kRGklv9/2R8775TeI2aXcdup1tKd7kE4ML5fBN5oeP8M+L8W0Rd/AzmkWzbyJ" +
                    "UwefwD889R8s/NNv8Ios7EQFnx75KfZlO3l1zw5OrG3g5Oqx/PqNpyHhBBw/steGt+r1I0bmKgTlqVIgk3bo9Rsp7nyId2/7e0JCCiZqsnr11Vc544wz" +
                    "+MlPfsKVV17J7Nmz2blzJ3feeWefkjutNb7vk8vluOCCC9i3b1+epjkw2ei9MVbg6QtRiMOkX1ESv9gO/GaPNIQGC5SQXDL0FFbP+RnnDp9MWmjWbW3l" +
                    "rOFTKbPLuO/FR/jP55dSbpdw7vAp4LooFEpY7ErvYa+b5ofTv0JT2VCUkOTQ1CTLqY+Xs8fr5MkdLyBL6vjv11cy+5FvsmLLKoTvMaNyDMNKari/dRn/" +
                    "+vzDJFWM5hFTwDcMLhuKNoZn216j2/foIsuX/u9W/mnlXWR0D75joUTgdQuavW47O/w9aARKSDJein1eO92ZTpoqG5haczzL257jztU/wUYya+SMMLPX" +
                    "oVBaWHVkNPmGBOPnRdT6REIFtUzmQ8/mHBL4XdfNt6dfddVVLFiwAICvfOUrnH322XR0dPRbkfPyyy/34eQty8p3NxpjCm9uIQrxFwF+XKKTFmi9v2Ly" +
                    "SJYIURm69OlxO/n55me49Nff4sVdr3D58acFJt5OOQMTJRhjmD16BkKW4PrB8rt1x+t8f83/MLKinktG/V0wj0iLrOeT813i2BQbG51qo7F0CF8YO5vG" +
                    "0lqMhPOaZgTH9xIcY5eggZmNp2KZYtI5FwEUWxb4KXJehuYTzuWs4afjEMuDhg4vWEkLJew8ACupENKBnM/5DZNQSIwfo6a4Cs9ozhkygdriWrSXOQLQ" +
                    "DmWTy2LhOilcFIiPNoBFtodKKebMmcMTTzyBEIJ58+ZRVlbWLxXTu5s2EmN7L/aJhShEIcASRQqTVEjfoJFh9c3hpnDgaYOvNb964w/Meew7kCwF3cNx" +
                    "1cfxqZrRgOBrJ8/CQmEMTKxqZExlAz1eQKk4yWK+17KY0xpPYHrNCYFOiZRsS23j2d2bOL1mHNePn8WiDTG+PuXzzGqYys3r7+e+dY9wfn2wB/Clky/C" +
                    "B3zjMbasnglDxrFiy1puPPESzhkxlZlbTqc8VsxPT78JT8Lgey8KAMRo/FC70oS9AVEDD8ZgtEfSKeP8xmlo4ILGk7joE58ipz2qkuU0D5vMj15cikzG" +
                    "8Q9zhWQwiLLQeDrqvjIfLUrnnUA/ytA///nP89xzzzFs2DAuu+wyli5detD3CzRNIQpxFDJ8MSCJtJ2w+sYcRrbau3FKYlkxlJQ4xaWIogE4ZQkkCT7T" +
                    "eDrldimPv7mGKQ9ex6QHv8gTW9aQkA6XNp0RZHxCknCKSUuX61d+n325TqSUxJQNIsdtT9/HGz07uf6EC1h9xX3MaphKy+7X+PEfHuPiptOpdEr5bVsL" +
                    "Jy28jpMfuI5fvrICpSSfm9jM8s1/YMGGR6kvGsQjF93Ff507D1/CHaseYE/72yQTxSghcezioPVAaCxf4sRspJQkVAwyWc5o+CQTKkfQ2rmVGYtvYML9" +
                    "V/CDdYsRQnD1cecSt0rQRh92lm+MwSpxAgFlE3gEfNTB/kDQ37ZtG3PnzgXg8ssvp6qqKniyhCi8kYUoxFEMoSbWGPWFE3BFIPJ1uCtmIQTG8xhfNYIJ" +
                    "ZcP4U8dbrN29AWkpfF9wcvVxjErWsmp3K6/uCcrmRlWMZFL1KLZ2bWPj7jc5a8gEtua6+X3bi/humlOOGcuxAwfzwq6NbGjfjDaahmQNUwePpaJoILu7" +
                    "9/C7N9axNbObSfXjaUrW0bLzdV7cuwEUDCtqZFrNWNq8Pazcug7LNUyvG8eoqmEIY7F+10ZWbXselGZUaT2TB41hS88ent72ItrRoCWnDRpPXXIQq/e8" +
                    "xGt7tzBu0Cgmlo3klc6trNr+PJCjqqiK0wafRMq4LH/rWVJeLiRoDon2ELNIbuoke9dqtC8xwv+44P3+ZWWoybNixQpmzJjBt771LW6++WYWLVrEFVdc" +
                    "8YH6lhaiEB9twD/tWCM+OwY/lwMpj7BCUICXAe0BDjJmI7RAS4Px0qBzYMdRsgiMxNedkEuBSoJMgNcJymCJUrRlof1OyKXBLkHIIqT08HUacl7QBCU1" +
                    "xOJIGUPn0sF5rRjKclBGkzNe4LWLRFqlaOWB2wGuDADZsiAWR3kS32TApEDbKKsEjQcWmFQOhAtxG0vbeK4HJgO2jU0SoySe74PbBUIi7OL94nCHA/iO" +
                    "omhHisztz+CnTK8CnY8P6keNUZMmTWLNmjW88MILjB8/noULF3LllVcWAL8QhThayRYDExgZVJccmtA5kGs2KCuBlAKfwL5QCIPQoKwESiQDfXntAx42" +
                    "SUQiGfDtCJRdhtB+YPCNjy2TqHgRGWEwZNFGYJGEeOgiYkRgk2gMtp1ACYErDL4JDEwsYSNiDsaABwijUPZAcIKRK23QHrgCLBVHiTie0BjfC+QZfIMp" +
                    "iiNFAuMbtDCoeAwpEujQ1N2gUcrCUhVB7TcaLQ+TbzYCJPglNiRsSGURkRvWxyiirtl169axdOlSLr744vznhShEIY4i4CcHlZKxYihLIAWhrksEsP2B" +
                    "/AE+jsIgtEFKg1QCYSTCBFaJJlQXVmGZopYWBo3t+ViBaA0GGylA+QaNwBcSx/hBlb9UwVg8vX+CCVUPMAZPGqQOugKMEAgNeAakwTJeONaIpjK4YTWM" +
                    "BPAEJgRg5YtAGcJI8CN5HJHXugl8aQP1CKWDdikdUjgKjcyXV+73sQ0SetP3FwtVoK2chZd0sPZkA6Otj2HlSXTN8+fPp7m5GcuyChU4hSjE0QZ8/euN" +
                    "mGe2IF0/AG9zAKiHYNn/u2jy84LoRVuYXpNEpLkTNR4RZt99aA7I18ocmPGayEqQg4a0H1pDwNUHHDP/ZS3yhTC9yZdItCwam9/7nOKAE4b/16YfCsa8" +
                    "w2JIm4OmSAOkAN3pIUTQb8tHv0in3yxfSsnatWt55plnmD59eqEypxCFONqAn9rSHgp89QLM9xCHIoXMe/z7/r5zqDoXc4hjmyMc4xFns/18JlFEJfj+" +
                    "xw/vg98gVLNcuHAh06dPL2T4hSjE0QZ8owJKhIi26EXiHA74HilY/iXHPLBa7yBcEO9+MGn6Hlkc6nxHm85AoEywc+FFFgIfQ6yLMvpIIfPdNPALUYhC" +
                    "vPf4f9XV6hP6yfukAAAAAElFTkSuQmCC";

    private static final String LOGO_SEULAMAT_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAQsAAABuCAYAAAAwGzYPAAABCGlDQ1BJQ0MgUHJvZmlsZQAAeJxjYGA8wQAELAYMDLl5JUVB7k4KEZFRCuwPGBiBEAwS" +
                    "k4sLGHADoKpv1yBqL+viUYcLcKakFicD6Q9ArFIEtBxopAiQLZIOYWuA2EkQtg2IXV5SUAJkB4DYRSFBzkB2CpCtkY7ETkJiJxcUgdT3ANk2uTmlyQh3" +
                    "M/Ck5oUGA2kOIJZhKGYIYnBncAL5H6IkfxEDg8VXBgbmCQixpJkMDNtbGRgkbiHEVBYwMPC3MDBsO48QQ4RJQWJRIliIBYiZ0tIYGD4tZ2DgjWRgEL7A" +
                    "wMAVDQsIHG5TALvNnSEfCNMZchhSgSKeDHkMyQx6QJYRgwGDIYMZAKbWPz9HbOBQAABA2klEQVR42u19d1iUx/b/ZwsLgnREmghCRCyxRsUaK8YSjcao" +
                    "idGba3Jjv7ZgEq8tsSXR5HrtxmjUgEoIWLCDJSqKBbGBoAZRBJHel23n90e+M793G4LBBM17nmcecffdd2bOzJw5/UgAEEQQQQQRngJSEQUiiCCCSCxE" +
                    "EEEEkViIIIIIIrEQQQQRRGIhgggiiMRCBBFEeKlBLqJABBH0QSaTQafTQSr9/S41/Jvo7+ltIIHoZyGCCP//QEgkTyUGMpkMAEBE0Ol0fx8iCmCRuEVE" +
                    "EAGQSqUgIowcORKbNm1C//79oVAoUFBQgOnTp8PLywuPHz9GWVkZiAhEBKlUqkc8RM5CBBH+JhyFu7s70tPTYWFhwb8rLCyEg4MDACA3NxdnzpzBgQMH" +
                    "EBMTg4cPH/6t8ERiE9vfvcnlcgJA//jHP8gUqFQqqqys1PusuLiYjh49StOmTaOAgAD+HplMRhKJ5GXEk7hRxCY2mUxGACgiIoJ0Oh3Fx8dTt27dqGPH" +
                    "jpSYmKhHJCorK0mpVBoRk7CwML13SqXSlwtHos5ChL+Fck4mg0wmg1QqhUQiMRJBdDodHB0dsWrVKtjY2GDEiBE4f/48Hj16hKioKFy/fh0ajQYNGjSA" +
                    "nZ0d5PLfDYlqtRpqtRoKhQK+vr7w8vKCVCpFdnY2lEqlUV+iGCI2sdXRJpFIzIoEMpmMZDIZyeVykkgk9OabbxIR0Z07d8jS0pJ/L/yNi4sLDR8+nLZu" +
                    "3Ur3798nc/Dw4UMKDg4miURi9A5RDBGb2OogoWB/DxgwgJYuXUqLFy+m/v37k52dndHzmzdvJp1OR//973/19BgSiYTrIoTP29raUv/+/WndunV0/vx5" +
                    "2rlzJxUVFZFarSYiovj4+JdNHBE3ldheTkIhlUrJ1taW9u3bZ3TzP3jwgLZv304jR44kFxcXkslk9ODBAyIi6t+/P8nlcrKwsDD5XlMcB2v9+/cnpVJJ" +
                    "Wq2WIiMj9fQhIrEQm9jqsMJy8+bNXAGpVqtJrVaTVqvVIxw5OTl0+vRpIiLKysriHIXQUmLKwsEIBxNjZDIZSaVSLp6MHTtWj0MRiYXYxFZHxQ93d3cq" +
                    "Ly8njUZDOp1Oj0DodDrSaDSk0Wj0PisvL6eYmBiaNWsWtWzZ0qSJ1RThYMSpRYsWpFKpqLS0lDw8PEQxRGxiexF8JsaNG0dEpEcQTIFOpzPiNoiItFot" +
                    "Xbp0ib744gvq3LkzKRQKkwpSptMAQLNnzyYiosOHD7+M5lNxc4nt5RRBfv75Z9LpdFzhWF3QaDQmf5OcnEyrV6+m/v37k62trRGBkkqldPLkSSIimjJl" +
                    "ynMXQf4Cxy9xc4nt5RNB7O3tKScnh3MOzwKM41Cr1UbvePjwIVeQurm5EQBydXWliooK0mg0FBgYSFKp9LlxFn8RxyJuMLG9fCLIkCFDqiWC1AQY4TAU" +
                    "WfLz82nPnj20bds2IiI6depUtfQcf8TKwwiiSCzEJrY/KIJ8//33REQ1FkFqQjgMFaSMG7lz545ZBekfMaNKpVJOcD755BPatGnTn81liBtMbC+XCGJl" +
                    "ZcXNl6YUl7UNzLJi2JdGo6HLly/TF198QUFBQZxQPMvhZhyTVCql1atXU0FBATVq1EiP0xCJhdjEVkOuonv37n9IV1EbxMMUR3P9+nUaPHhwjQiGUHwJ" +
                    "CAjgCtQOHTr8FboLcZOJrW61Zz0A7Pb95ptvnqsI8iwKUiHX0bdv36eKJFKpVO/7adOmUUFBARERvfPOO3+Vs5e4OcX2chAM5kV58+bNP00EqQmoVCoe" +
                    "M2LOUmJIJLp06cK5CSKif//733+lV6i4McVWd5q9vT117969xspA9mzr1q1Jq9X+ZSLI0zgNnU5HxcXF1LBhQ72oWMN4k2bNmtHWrVv1CN4nn3zyV7uP" +
                    "ixtUbHWHm7C0tKR9+/bR0KFDa3Qw2HPz5s2rEyKIOWJBRFRUVESurq4klUqNgtVatmxJGzZsoLKyMr3fzJ49uy7EmYibVGx1S0HZvn170mq11KtXLwJg" +
                    "MvrTnCXk/Pnzte5fUVvAxhQfH6/nc6FQKOiNN96giIgILqowjqKyspLGjx9fVwLSxE0qtrpHMNavX09arZaCg4P5QTHn1MRkfz8/P6M8mXUJGLfz4Ycf" +
                    "EgB67bXX6Msvv6Rbt27pPcdS9qWnp1OPHj3qUuSquEHFVrd8JWQyGVlbW9Pdu3dJq9XSxIkTq3RqYgdp8uTJdVYEEXIXYWFhdPnyZSMnL2Fez3379pG7" +
                    "u3tdC3EXN6jY6iZ3ERQUxFn3jRs3ko2NjUkug3EWR44cqbMiSFXchkql4rqJ/Px8HoRWBxPniJtTbHU3xmPSpEn8YF29epU6d+6sd5AYoXBzc6OSkpK/" +
                    "1BmrJtwFi2wVjvWnn34iX19fI9dukViITWzVJBhfffWVnq/C0qVLqX79+lz5KZPJaPTo0aTRaKiysrJOEwvmpCWEgwcPUs+ePesqNyESC7G9WARj9erV" +
                    "eo5NSUlJ3JMRAEVHR5vMSfFX+1ywuBFDAlFeXk579uzhCkzGTdTxZDnihhRb3Vd4AqDvvvuOmxMZxMbGUt++falz5840b948io+PN6ngZDe6Wq3mafZq" +
                    "m4gw924hoTKES5cu0eeff07+/v4vEpEQiYXYXjyCERISolcZTGg96Nq1KwEgJycnevvtt2nTpk2UmJjIdRlVhZozQsKISVVN+Cx73hwUFhbSmTNnaMmS" +
                    "JdSuXTuTaflemHVgFEMEEeoySCQSSKVSaLVaDBs2DN9//z1cXFygUqkgl8shlUoBAImJidiwYQNCQ0NRVlYGAGjYsCHatGmDNm3aoFWrVmjatCm8vb3h" +
                    "4uLCK6DXBhQVFeHhw4e4ffs2EhIScOXKFVy/fh2PHz/We04ul0On00Gn071YayASCxFeJJDL5dBoNPDz88PGjRvRt29fADAiGiUlJTh8+DB27dqFo0eP" +
                    "oqKiQu89Tk5OaNiwITw8PODh4QFPT0+4uLjA1dUV9evXh4ODA+RyORQKBaRSKTQaDdRqNSoqKlBYWIji4mI8fvwYWVlZyMjIQEZGBh49eoScnBwQkRGh" +
                    "k8lkLySBMASR1RXbC+mHAYBmzJhB+fn5XGfA6oMIITMzk8LCwmjcuHHcNPm8x/cyVlMXOQsRXkhgBY61Wi18fX2xYMECjB8/HhKJBEQEjUbDRRfGbQBA" +
                    "aWkpbty4gbi4OFy4cAHXrl3DgwcPUFlZabYf1hcrcmxY7JiI9Joh6HQ6k5+/cKKgSCxEeFkgKCgIISEhGDp0KD/QGo3GSBwQQmVlJe7fv4+kpCTcvHkT" +
                    "KSkpuHv3LjIyMpCXlwelUiki9q8mFuYoNaPA5qi0CCJIpVLodDq8+uqrkEqlSExM1Pu+c+fO+OijjzB8+HA4ODjwz4WEwxzxYFBWVobc3Fw8fvwY2dnZ" +
                    "ePz4MXJycpCXl4fCwkKUlZWhqKgIGo3GJFcilUphaWkJKysrxMXFIS8vj3M9NT0nVeoQ/uQz8qeav1gxlur+5mWU/Wobr+bay66zCA8PJ61WS3v27KFB" +
                    "gwZRvXr19J7z8fGhmTNn0rlz54zMm0K/C0MzaG36X5SVlZGzs7NeGL2os6iCMspkMiOqbm1tDRcXFzg4OMDW1pZT3bKyMhQWFiIvLw8lJSV6v5HJZCCi" +
                    "F16jLMIf209EBFtbW6SkpMDd3Z1/l5ycjF9++QVRUVG4evWq3q3btm1bBAcHo1+/fmjXrp0exyHULbC9JfytKeuGkDs2dftrtVpIpVKEhoZi/PjxkMlk" +
                    "0Gq1NZ6vnZ0dLCwsQEQm+2HczQsvhggRZGtrix49eqB3795o3749fH194erqCisrK6PfqVQq5OXl4f79+0hMTMTJkydx8uRJ5ObmGr3373xg7O3tsWfP" +
                    "Htja2kKn03GFn4WFBY4fP46FCxdylv1lAbb2wcHBOHLkCN8HTJnJDvfFixdx6NAhHDt2DAkJCVCpVPwdrq6u6NixI4KCgtCuXTs0a9YMHh4eUCgUtTZO" +
                    "rVYLmUyG0aNHIzw83OSFWZ15hoWFYcCAAfx9pvQ0KSkpf8o6PzdiwSbboEEDTJ06FePGjYOPj4/JZ4WTFGquhZCdnY09e/bgv//9L9LS0l66Q/AsxMLZ" +
                    "2RlZWVmwsLAwembv3r146623XjrCyvws1qxZg6lTp0Kj0UAul+txBuz/DG7fvo24uDicOXMGFy9eRHJyshG34O7uDn9/f/j7+8PHxweNGzdGgwYN4Ojo" +
                    "CGtra9jb2/P3q1QqaLVayOVyeHl5oX79+kZ6N4lEgpKSErzyyivIzs6usb6CrdvBgwcxcOBAs8+1bNkSt27d+tPOw3OTKd9++216+PChkZzICrKYcp01" +
                    "9b1Q3iwsLOTx/nU0jPdPK6bj5OREubm5enhSKpWk0WgoNDS0rkcwPvO8FQoFpaSkmM3gXVWNUpVKRXfu3KH9+/fTsmXLaOzYsdS5c2fy8PCoElfW1tbk" +
                    "6elJ7du3p7fffpuWLl1KsbGxVFxcbNQH6/fgwYPPnKmcjWXfvn28DgmLZxG25s2b/2n1Q+TPi6MICQnBV199xbXQMplMzzZeHTdb9jtGlbVaLezt7bF2" +
                    "7VoEBARg+vTp/Kb5u4JMJuO6HIYnhuuXlaNq1aoVmjRpAp1OZ3Kewltcp9Pp7TULCwvOQQwZMoR/rlarUVpaynUAubm53CvU0dGR/8s8Ow33qVA/x/QY" +
                    "+/fv17Pe/JF5m9ON/Klc3fMgFGPGjMFXX30FrVYLiUSixyZKpVJ+wFNSUnD//n08fvwYarUaMpkMbm5uaNy4MZo2bcr1GcLfMYebadOmIScnB19++eXf" +
                    "XofxdwF26MaNGwe5XA6tVmt08TAFuJBgVlRUID8/H0VFRSAi2NnZwdbWVk/JaWFhAUdHRzg6OgIA/P39q9RHMPFHJpMZEQ+pVIry8nIcO3bMSMx+FrFL" +
                    "q9XWCTeCWiMWEokEOp0ODRs2xLp16/hNJ1Q6SaVSFBYW4rvvvkN4eDhSU1PNItLPzw/Dhw/H7Nmz0bBhQ04wGPHRaDRYtGgRDh06hISEhBoTDCG1rqlH" +
                    "3t9FLyLEkzn8PK++hXtH+LlMJsOFCxfQrFkz9OrVi1sKhHtMJpPh/v372Lt3L44fP47k5GTk5+ejtLQUAGBjYwN7e3v4+PigU6dOGDRoEHr06MED1arC" +
                    "BdNVMAKVlJSEixcv4urVq7h37x4yMzNRWVkJtVqNBw8eGHF9NcX/rVu3MHDgQK4v+as5xlpNUjJ//nyjpKksAUl6ejq1aNHCpB89a4ZyY6NGjXhyU6F8" +
                    "yt4fGRlZbdncVDGX6syrJn4htenz8LT3OTk58ZJ2TG5meNm1a1eNdRY1xQ/zg6kOfqqDF2EoenVamzZtaMeOHXr6gidPntDUqVPJ3t6+Rrju0KED7d69" +
                    "W2/PmtKDsLof69ato6CgILK0tKwRfp+GL4YXluPCycmJdu/ezZP+GOpHmjdvzp9/3r42tWYNYbfBlStX0Lp1a07lGRsmkUjQr18/xMbGQqFQQKPRVHk7" +
                    "MbFDpVLBy8sLiYmJcHJyMpJJlUolmjZtioyMjCplQ0POo379+npabysrKxARSkpKkJWVhbS0NNy/f19vPHWN03BycsK9e/fg4ODAby/GHu/evRtjxoyp" +
                    "Nsdl6jlXV1c0bNgQDg4OHD9lZWXIy8tDTk4OCgoKjKxYz8pyC/tXKBRo1aoVAgIC4O7uDrlcjsLCQjx48ADJycl66wIAgwcPxvbt25GcnIz33nsP6enp" +
                    "/DtnZ2e0bNkSfn5+cHFxAQDk5+fj4cOHXAwWwvDhw7F582Y4Ozvzm5ytuUQiQWhoKBYsWIDffvtN73cWFhZwdXVFgwYNYG9vDysrK2i1WpSWliIvLw9P" +
                    "njxBUVHRM+8nHx8fhISEYOLEiVzMIiIEBgYiJSXlxRFD2CF1c3PDK6+8ose2MoTfunULsbGxkEqlejZvc8BMVBYWFsjIyMDmzZsREhICtVrNTYVarRaW" +
                    "lpbo0qULwsPDTSqAGBFjzw4bNgwjR45Ep06d4OXlZbZ/pVKJlJQUHD9+HKGhodyl2PBQsbl7eHggPDxcT35lLGtYWBhWr15drYPL3te5c2esXr2aE1qh" +
                    "sm7BggU4evRoreRiECqPra2tMWDAAAwePBgdOnSAt7c37O3tTcrs+fn5uHPnDn799VdERETgypUrRso8Nt/x48dj0qRJJhXbEyZMQHJyMrRaLZycnDB9" +
                    "+nSMGTMGTZs2NTneiooKXLlyBdu3b8dPP/0EpVKJ6OhotG/fXs+RLzAwEDNmzMCQIUP0HLeEUFZWhkuXLmH79u0ICwuDSqVCZGQkUlNTcezYMbi7u/O5" +
                    "6HQ6TJkyBZs3b+a/d3BwwIABAzBw4EC0b98eXl5esLOzM4mv3NxcpKam4uTJk4iMjMS1a9eMiIZUKsXu3bvh7e3NiT8Tx0pLS7nzolAUiYqKQkVFBcc7" +
                    "e5dGo4FUKsV///tf7Nq1q9b0erVWxLZ58+acTWL/MrPn/v37a8xmQlByvmvXrmZdar/99luT9RWEbNjYsWMpKSnJZKZllUrFm6l0aBqNhnbs2EFeXl5G" +
                    "rD2bu6+vr9nxrVmzptr1H9i7Bw4caPZ977//PgEgV1fXPySGCPEzefJkunPnTpXZpLRarVlTZUREBE8Vx/o0FE1NAUtUO2DAAEpLSzOZu5I1w76vXLlC" +
                    "HTt2NJrXtGnTqLS0VO9dhuZ5w3ddvHiR2rZty9/x2muvUXl5OalUKtJqtTRy5EiOM4VCQbNnz6b09HSz+GLNlOs4M20b7iepVGr2nc8KISEhtVl7pPaI" +
                    "RcuWLY2Qw4hFTEzMM9mD2YZ2cXGh8PBw2rt3L0VGRlJUVBRFRETQvn37eBEa4cFgv6tXr56eXMtSo5mzwxsmexXqXh49ekSvv/660QLj/+IQysvLTfo8" +
                    "rFy5ssbEon///pyQsTFXVlaSRqOh0aNH/2Fiwcbt7OzM620I5y48qMLNL/zcMJ19Tk4OT20nlUr5fOfOnauHD2EfPj4+1LdvX354GcEWrlNVCXDLy8up" +
                    "X79+fF7/+c9/9HwqhOnwDA+v4buKi4t5UWb8X64MIqL//Oc/fE6enp50+vRpo/1UFb6EcxLup4yMDD0CpVAo6O7du0brbg4Xhv0LG8P1jBkz6iax8PX1" +
                    "NVLEsH9zc3PJzs6OK3mepzMVU/hYWVnRsWPH+CFiyDZEemFhIWVkZFBGRgaVl5cbVb0WHsLy8nLq1q2bUaJVHx8fnhPS8DerVq2qMbEIDg42GisjvGPG" +
                    "jPlDxILhx8bGhuLj4/nBYn1VN5BK+Bxb9/z8fPLx8eE3MAD69NNPTVYK0+l0tGLFCsrNzTXKqWmuTqipcoCFhYXk7OxMQ4cO5WNh46npu3JycqhRo0Yc" +
                    "P6GhoVSvXj2ubGSlBoX4MnWIzXEchvjKyMggNzc3kkgk5ODgwHFRGyUHiIhmzZpVa8SiVnQWTEmTnZ2N7OxsPV0A0xc4Ozvj66+/xsSJE7kTi2FiEqHC" +
                    "x5Tyx5yMbhhcxkxgmzZtQr9+/fT0HEyHUlBQgO+//x779+/HnTt3uFnNxcUFbdq0wbhx4zBixAj+fmbvrlevHnbv3o22bdvysOMX0V9Bq9Vi+fLl6Nix" +
                    "I1QqFY+LEAYsRUdH4+jRo0hJSeH5LO3s7NC8eXMMHjwYvXr10lPwqdVqODo6Yvny5RgzZsxTcSORSDB37lz+f4VCgcrKSty9exdPnjyBXC6Hh4cH/Pz8" +
                    "9EyQQh8EnU4He3t7nD9/Hg0aNDDysUhNTUVWVhYAwMPDA6+88orZd2k0Gri4uOCrr77Cu+++C6VSiXHjxvH9tXr1ajRv3lxvPzFdg1arxb59+xAbG4vU" +
                    "1FSUl5dzvUaLFi0wbNgwdOnShffL8OXp6YlFixZh4sSJkEgkUKlUUKvVRk525va+Wq0269DI9mydM52yG2zXrl1cRjRF6aKioqhbt25G4cR4SooyZnJ6" +
                    "GkfCxvHWW2/pUXDhGM6dO0dNmjR5at+jRo2iiooKPfaVzWvjxo16Fb5fFM6CcUL+/v5crBFygTqdjrKzs6lPnz5PHevHH3+sp8dgv1cqleTt7c2fM8dZ" +
                    "GHJsCxYsID8/Pz1R1dLSktq1a0dbtmzRM8Ob43DY32vWrKFXX31VrwK7QqGg1q1b07p164w4R8OQhICAAL01a9OmDRdbDPdTWloaBQUFPRVfs2fP1vsd" +
                    "659xRlKplHx9falJkybk6+tLr7zyCvn6+tKaNWv0cMXGXFJSQq+99preb4TNz8+PHBwc6l4pALYpe/ToYXZjCDd+WloaxcbG0o4dO2jZsmU0adIkGjJk" +
                    "CLVv354aNWrE61qa830wRTSYbVmhUFBSUpLe4rJNdvv2bY5ACwsLk/ZpmUzGN9k///lPvYPK7O1KpVKP4LwoxIKNYc6cOZyYsk3L5GJWudzCwoL7vgib" +
                    "0B/mwIEDemNj/zK9ytPEEK1WS4WFhVzXISRqhmvMCh9XJburVCoaMWKE0bsMdWWsgpkh8WFjZIpBdqktWbKE5/gU4quyspKXVKwKX6z/U6dO6eGJzeWN" +
                    "N94wuycWLlxolljU1J+kThAL4a21ceNGLoOasiw8TSYuLy+njIwMSkhIoAMHDtC3335L//jHP6hVq1Z6G8jcQRg0aJDRpmJ/DxgwQI8jeJoDDQCKi4vT" +
                    "W2C2aPPmzXvhiAX7f0REhEncJyYmVmus7GDMnDlTr1/2L1MKVkUs2Hw++OADzkUYHmpD4v3LL79U+S7Wr0KhMCI4TFfDdCnffPONkQ5DaL0T7pOYmBiT" +
                    "+Dp9+nSN8MVKMQrxpdPpaNKkSXrjZuOUSqW0ZMkSs8TCzc2NpFIpr/tq2GpTN1irsSFMfps2bRrs7e0xevRobmdmMqrQecdQL8HceevVqwdPT094enqi" +
                    "bdu2er4X165dw+7du/H999+joKBAz37M3jVy5EguZzL7s1QqxbVr1xATEwMLCwujACNzjkIA8NNPPyEoKEjPOYeIEBwcjKVLl75Q+go2B2traxQXF3N7" +
                    "PMuDkZCQYNI12dA9XiqVorKyEjY2Nib7Mfe5cC1lMhlSUlKwY8cOyGQyqFQqo36ZDwjrc+PGjRg+fLiRrksmkyEnJwf/+9//IJVKudxvyk2dzXnNmjWY" +
                    "MmUK6tWrp+fXwMINWAkAptMwha+LFy9W6d9jiC9T+VskEglcXV05XoS+HU9L9sueeZaUfX9ZbIhwI2o0GowZMwZnzpzB3Llz4e3tXWVsgdD3XqjgFD7L" +
                    "CEnbtm3Rtm1bTJo0CVOmTMGhQ4d4TQa2gF26dDEiTFKpFOHh4TWKUGVE6NixY3rEhcWoBAYGwtHREQUFBS+MopNtxHfffZcH5jG8S6VSVFRU8LkY5okQ" +
                    "bkaNRoOAgAB88MEH/LDWlFhIpVIcOnSIO69VtdlZMNWtW7dQXl4Oa2trPna2NvHx8SguLtbzujTXt0Qi4QWB2rZta3R52NjYcA9iqVSKESNGcDw9K75a" +
                    "t26NESNGmMRXvXr16vzekT+Pm4sd1PXr1yM0NBRvvvkmBg8ezL3cLC0tn3qrM2QLw34Z4dDpdPDx8UF0dDTeeust7Nu3j2uzPTw8OHFii8iIhlwuR8+e" +
                    "Pc0GDJnzbmSVr5jLM3uvs7MzGjVq9EIRCwaFhYU1et7S0hINGzaEr68vWrRoga5du2LIkCGwtbU1mfLNMBLTHFy/fr1G4ygrK0NZWRmsra1NWuPY3nua" +
                    "2zkjMjk5OWa5SrZHdTod8vLyqkWEGVhZWcHd3Z3jq3v37hg8eLAeF1PV7/8WxELIDchkMhQVFWHnzp3YuXMnLC0t4enpCQ8PDzRq1Ig3Ly8vuLu7w83N" +
                    "Dc7Ozqhfv74RMTF0f2Wuw9u2bUOLFi14iTgPDw9YWlrqLQgjFosXL64V92jhzdigQQO9z18UYOM1rJQllUp5ioCAgAAEBgaiWbNmaNy4Mdzd3Y3YaGGm" +
                    "qpqabwHg0aNHege+JmM39c7qvoe9w1y9EOEYW7duDScnJ5w6dYpfSqwfuVyOxo0bIyAggOOradOm8PHxgZubGywtLY1EKlMXZW2WUXwhiIUpaslChnU6" +
                    "HSorK/Hbb78ZBeEIby8nJyc0aNAA3t7eaNWqFfr164devXrpsYAMuRqNBo6Ojvjwww/x5ZdfAvg9QEwoExuyss9KwQ03Ilv0F4F9NEfQhXkhu3fvjhEj" +
                    "RqBXr14ICAjQ2+TC9VSr1XxNq+IghDf/0ziF2haxahtsbGyQl5fHdSEA0KdPH4wYMQI9e/aEv7+/yfydhviqqvTA345YmKLqQocpczkk2DOVlZXIyspC" +
                    "VlYWrl+/jujoaCxfvhyvv/46du7cCU9PT72YfkZABg4ciCVLlpjNgGyKtfzDiPu/Q/Ist2pdAKYYDgoKwrJly/D666/rfa9Wq3k+S6GuhuE+MzMTt27d" +
                    "Qr169dCtWzcj3JsiNjXhEuqSficuLo5/1qtXLyxduhRBQUEm8WVhYcFxxPCl0+mQkZGBmzdvwt7enivLXzRutFZ3urOzs17acvavWq1GXl7eU0NyzaVY" +
                    "P3XqFMaPH4/jx48b3fYSiQR+fn5wcHBAQUGBnuXFUIQ5c+YMHj9+zD34akM3c+/evedyqz3PJCdMjJswYQI2btyoV9WbcVDCyN779+8jOTkZ169fR0JC" +
                    "ApKSkvDw4UMUFRXhgw8+QLdu3biS8mUE5m05ffp0rF692kinxp5hYllaWhpu376NxMREXL16FcnJycjIyEBxcTEmT56MoKAgI3y9CLir1RD17du3o2fP" +
                    "nrxmAtMYp6am4rXXXnvqgTK0hLBDL5PJcOLECaSkpCAwMNAoY1D9+vVha2uLgoICrogSfs9EkoULF+LkyZPPja03d1M8izzOdCG1nR2JcRSvv/46tmzZ" +
                    "oidHC9csLi4O27Ztw7lz55CWlmZUxo/pjpjYZ47zetGBXXaDBw/mKQOE1gy27rGxsfjpp58QFxeH9PR0I10I28csJ4upi7ame+WFJhZ5eXmoX7++0cFp" +
                    "3rw53NzcuI9+TRAiNJ0+evQIgYGBJn0A2GcZGRkoKCiAo6OjUTqz1q1b48yZMzWu4VCVDZuJUCqViuffMARTOQ6eBq1atXquRI35hwhvOGZSnDVrFr77" +
                    "7jujNRaaohkOPT09TRK76uosXgTdjoWFBZYtW6anuGd40Ol0mDRpErZs2WISX8LfqNVqIzcCBqb8L+oa1Cqvm5CQwJ1ehA4wVlZW6NGjh0n7ck0XzRSw" +
                    "gwoAeXl5SE5O1hMN2AYOCgqCRqOBRqPhyV6r06p6nr27pKSEB6MZihLt27fnB7O6cnK/fv1qXRxhRN3f3x8dO3bUWw/GDe7YsQPfffcdT0TLRD2dTsdx" +
                    "x3xaiAg9e/Y0Oc6XIbs4m8Orr77KiTfDF3POWr9+PbZs2QK5XG4SX0ypzp7v0qWLSfy8CLlea2VF2QY/ffo0Z0GFegciwieffMKRUpONxJBvbW2NgIAA" +
                    "PUQzBD958gR5eXlcF8GyKrPv2fN9+/aFs7NztcfANNhdunTBzZs3cf36ddy8eRM3btzgjWWBrqysRHFxsVG/RITWrVvj1Vdf5dGrVcnGOp0OwcHBaN26" +
                    "da2LIIywBQQEcD2FoegUERHBcc4Ig6noX51Ohw4dOqBjx451IpHs8yQWLVq0MCL27LuIiAi+78zhizmc9e7dGy1atDCJrxcBf7VGLKRSKa5fv47Lly/r" +
                    "IZYhsl27dvj22285pWVadrYxDRu72ViI+6RJk+Dm5qZ3m7OFSUxM5JQbAHbt2sVNVoztZmnbQkJCeP9VaaOFbOT8+fPRokULtGrVCi1atEDLli3RsmVL" +
                    "ODs748GDBzxV4O3bt42UuIxlX7VqFb+R2NxZY7eSWq1GgwYNsHbt2mrdNFXVkmA4FDb2rKurq0lXe8alVXXTCTnDlStXmvVteFnEEHNipNBPoyrFPdMR" +
                    "yeVyrFixwixun5VYGK6xqVbnxBDG4n799ddGMj7Tvs+cORM//vgjvLy89Fg0w9T7TOnGnvnoo4+wbNkyI4rMDsuePXv0DmZqairCw8P1PDXZos2ZMwej" +
                    "Ro3icQgsMbCwsbmo1WqEhIRgwIAB/NZg+gkA+PHHH1FRUcFt7CdOnDCaO7uF+/bti9DQULi4uBiJNYy979ChA44fPw5/f3894mdOYapUKnneBFOimVar" +
                    "5f8KfUxYHg7DXKkAuCLawsLCiKAxHGq1WmzYsAE9e/bk9V4Mobqm0xcBDMVL4WXIOCuFQmEWXwCwbds2tG/f3iy+qgJzjmNExC2AVYnKdUrBKZThfvnl" +
                    "Fxw8eBCDBg3SSxLCDs348eMxZMgQREVF4ciRI0hOTkZ2djbKysq4XsLe3h7e3t7o2LEjRo4cyeU8w8WSyWS4ceMG9u3bxwkD41Q+//xzDBkyBPXr1+dE" +
                    "Rsh5tG3bFuvWrcPDhw9NWmleeeUVhISE4MMPP+REiMmicrkc+fn5WLt2LdeWA78nT12+fDkUCoWRB6lOp8O7776L119/HRERETh//jxyc3NhYWGBJk2a" +
                    "oG/fvhgyZAjHkzn9jBCKioqQlpYGNzc3Pka2EXv06IGwsDA+htLSUsyZMwdFRUVITU3luBISNSLCtGnTEBERYTZjdIsWLbBixQoMHjxYb31fRmBEOSkp" +
                    "yYirYhzV7NmzER0dbZQlnEG7du3w9ddfo0+fPtBoNEb4EjoZmgNhtnLhb6ysrBAWFgalUmk2+K+oqAizZs1CeXl5rQSb1WqIukQioQYNGlBycrJevgRz" +
                    "Kc10Oh3l5+fTgwcPKD09nbKysqisrMwoD4apnAMqlYrnEhCGNrMw7OHDh+vlWjRMeFJUVERHjhyhVatW0bx58+jzzz+n1atX04kTJ3h6PcMwd/aeUaNG" +
                    "6fXF/v3f//5nlHSnqnRuppLBqFQqOn78uNl8pqxvAPTFF1+Y7c8Qhy4uLhxXly5dMkrmIsyluWTJEnrjjTeoS5cu1L9/f5o2bRpFRUWRUqnU6y8mJoYq" +
                    "KiqMxnj58uUqQ9QZXlkei6clcmah1vb29pSTk2MyLH/Lli01TgWwf/9+k/klHj58SNbW1jxsPDU1VW/9hf1nZmbSwoULKTg4mLp27UrBwcE0Y8YMOnDg" +
                    "AH+e4Ss2NlYPD+z7uLg4oyTKwty25pIlPw2USiXZ2dkZvRt/dT4L4QS9vLzo/PnzeodAmCHIsOCxqfyOwmfYxmb/Lysro+HDh5vdaOyzCRMm8H7ZGAwP" +
                    "ydNyNRom7p05c6bJBMFSqZTs7e3p5s2bJgml4bzYBlCr1TxrFRHR5MmTqUmTJkZZoAyJhVQqJW9vbyouLjbqj/XDksXm5OSQs7MzH++bb75plEuyuvk3" +
                    "GXHYsmULSSQSys/PNyrEc/36db4XXlRiUa9ePd7v+++/b3JNn4YvliiJiOjrr78ma2trvbwnhsTV8ECzC/jkyZM8R4zhnjBVXJyte2ZmJtna2tZNYiEk" +
                    "GFZWVrRs2TIqKSkxmZbdXGZiw2zJhgf7zJkz1K5du6duMvZdnz599MoACMdQWVlpsrHvhQuTlpZGQ4cONdsvm3fjxo3p2rVreoTSMOuzMFO28LAuWLCA" +
                    "AFCXLl30Fl2tVpNSqSS1Wk3vvPMOv/HYwRfe7sJ3s98/efKEEwt2mJYvX643RiEBE2aXZn0LE+r++OOPJJVKycHBgbKzs3lfDHfXrl3jOAoJCSG1Wk0V" +
                    "FRV6eFer1dSlS5caE4usrCy9ubH3bt68ucbEIioqSg+3bFxpaWk8SxZ7lqW3exq+VCqVEb42bNhAAMjd3Z0KCwv5+Fm/Fy5cqJJYtGzZkp48eVJlqQRT" +
                    "xOLRo0d1m1gYigV+fn60bNkyun379jNnKy4sLKT9+/dzbgLVLM3HnrGxsaFZs2bxW78mkJSURPPmzSMnJ6en9svmbWdnR99++y0VFhZWq4/z589T//79" +
                    "+aJ269btqXVDhOna2rZtSz///DO/5U2BUAxhc5g8eTJlZ2dXGxepqak8sxX+L7tVXl6e0XMZGRn80C5atOipdUOqSywcHBxMpmwUZgerCbE4ceKEyXeV" +
                    "lJRwMUSYlm/OnDk8O1l14MaNG3opBh0dHU1mMb9z547ZAy3Mm7pjxw7OWVUHKisra00MqbXyhebMd0KLhIWFBVq3bo1OnTqhVatW8PHxQYMGDWBnZ8e1" +
                    "5xqNBiUlJSgsLERGRgZSUlJw9epVXL58mXuACpWG1TUvsTHI5XJ06tQJPXv2RJs2bdC4cWM4OjpyD7rS0lLk5+cjPT0diYmJiIuLw8WLF7kSsyZVxQCg" +
                    "UaNGGDRoELp3745XXnmFx8+Ul5cjMzMTV69exeHDhxETE8PHp9Fo4ObmhuHDh+spSpmiNiYmBqmpqXpZwFh/rq6uCAgIQOPGjVG/fn1u9aisrERYWBgq" +
                    "KiqMxunm5oYxY8YgODgYgYGBcHJy4r4BJSUlyMzMREJCAg4fPoxDhw6hvLxczyw9evRoXkJRqFjbs2cPNBoN2rdvj06dOulZs9hvo6KikJWVVW3lm0Kh" +
                    "wHvvvaeXF4K9Nzk5GSdPnqx2PgsWhOjj46MXFyORSFBWVoawsDC97NnsvY0aNcKYMWPQv39/NG3aFE5OTlwxXVJSgoyMDFy5cgWHDh3CoUOHuAWEVcUb" +
                    "M2YMT97DxpKbm4uff/65ynKebE4uLi4ICAjgFePMFa+WSCSoqKhAWFhYlaH41T7Pz5NYGPosmHOxFkY2MpNlVQ5az2IOMiRchsSEOUuZSu3Gxsi8Fv9I" +
                    "fywqkUUpmiNqz4LjZ8GNYZ9WVlZwdHTk1pHS0lK9Gp2Gv/kz0rnVJTDEl7W1NRwcHDihKSkp4c55NV3Xp+Hyj+z/Wrn8/wxiYegXUVUeTkPEMAQ+LRdh" +
                    "TYlGVe+tzjM1JZSm3lPVd1XlPqiKaAnHbsq8XZUDlzAJjjlnLHMenaZuNrapDevDVHcuZu39Zrxgqxp/VYffFK6E469tfJkaf1X91WSNzbk1vHDEoqrJ" +
                    "m7Jvv4z9G4bO1zUQpglgY/w7cQ7PegEK1/RlxVedIBYiiCBC3Qfp323CVcVTVOf7P4OzEeHp+PmzcPZX7Ie6un9qjbMQynFMTmXyqJClNfe7Z5Fdn6Yr" +
                    "MJUT9Fn7YOOsNfmvCpyYkn9rS2fzZ4A5eV0Yvv0sc6kJzgz7fF5KQaEOzlCUfREydtd5MaQ6GvTqatlrqo23srLSCwYzfIdCoeCmRlPfP62/qr43579v" +
                    "ZWUFpVL5pxygmuKrpvOprX4N8cMC4mqCs2ddK3P74XlbfsztD1Pry55lwY+GmczqpBjC2DRLS0ssXrwY8fHxiI+Px9KlSyGVShEQEIC7d++iWbNmetp/" +
                    "Fp7u7u6OOXPmwMHBgd9KQnu3YbCT8G9hNWxhklQACAkJweHDh3Hq1CmcOnUKcXFxeOONN7Bu3TqsW7cOwP+PjCQi/vfmzZuxc+dOAL+bOdmitGrVCjNm" +
                    "zNDTZAu5F6H1xFD7L/yORah+9tlnOHLkCAICApCamgp/f389C4jwvbt378bYsWNN4sAwe5MhW2oYqMTGJ3zG8J2sX3PrIPxOaLVih2vr1q2IiIjgqeLY" +
                    "OOfOnYvo6Gh06NBBb61McSbsOz8/P6SmpsLb2xvTp0/nUbmpqalo2rSpWasRw/fixYuxe/duvr5sLqbmJsSRpaUlJBIJ/ve///EIZqEVgj07duxYnDt3" +
                    "DrGxsdi7dy9iYmJw7tw5LFq0SM/SYhiEZhg9LcSncDym1leY2mHixIk4f/78nyKS/GFiwSa5fPlyTJ06FRs2bMCOHTvw+eef48iRI8jJyUF0dDSKi4u5" +
                    "qYn5LOh0Onh7e+Obb77hxIJ9zhAlZOWErKQw/NYwgzgABAcHo0OHDrh48SIuX76MCxcuICcnB40bN0bDhg2NuAf2t7u7Ozw8PHgaNLlcDoVCgc6dOxul" +
                    "mmPsNHOYEToJCcctZLsZR5OUlIQTJ07AysoKXl5evICRVquFtbW13qKPGjWKl3EUfm6ID8NbiB0YoZ1fONaq3sPmYGodDEvsGYbkv/nmmxgxYgQvX6nT" +
                    "6eDp6YklS5Zg0KBBZlPLsTELw+lLSkrg5eUFW1tb3L59G7GxsbC0tISXlxd3zDLEGTts3t7e+PTTTzFq1CgMGTJET6QTlvxjDllCzoXlqfDw8EDDhg35" +
                    "PA3N/VlZWYiLi8O9e/cwdOhQZGdnIy4uDsnJyXo1QoQ4Ntwf7L2Ge8dwXQz/ZpyFn5+f3rueJ9RK9fT4+Hg6e/Ys/7xJkyY0adIkCgwMpBMnTpCHhwf1" +
                    "7duXLly4QMnJyXTs2DHq2bMnJSUlkVKppPv379PQoUMpMDCQLly4QHfu3KHLly9T8+bNacaMGRQTE0PDhw+n1NRUSktLo3/961+0YsUKevDgAcXHx1PH" +
                    "jh1JIpHwQrbHjx+nqKgoo/H++uuvFB4eziM27927R2lpabRmzRpeFfzkyZMEgD777DP69ddf6YMPPqDMzEyqrKykmzdv0pdffknnz5+nDh06UEJCAi1Y" +
                    "sIC6d+9OiYmJdP/+fYqLiyN/f396//33KSEhgYYMGUKJiYmUkZFBmzZtIqlUSh9++CHt2LGDWrduTTqdjho1akTOzs508eJF+vzzz3lMgEwmo8LCQvry" +
                    "yy/1Kr9bW1vT2rVrKS0tjdLT02nVqlVkbW1Nly5dovfee4+79u7evZsWL15MH3zwAd25c4fu3btHBw4cIDs7O1q2bBlFRkbS2LFj6c6dO5Sens4LC3fv" +
                    "3p0uXrxId+/epYSEBOrYsSO1bduWkpOTaeDAgXT27Fl6+PAh7dmzh5ycnHh/9erVo9TUVEpNTaUbN26QQqEgiURCixcvpidPnlBFRQX16tWLFi1aRKGh" +
                    "oXxd1q9fT1u3biUANGrUKD7WDRs2kFqtphYtWtDYsWPphx9+oMDAQNLpdOTj40MuLi4UHx9P8+bN427ZzN179erVdPbsWVq5ciVdu3aN71d/f386evQo" +
                    "paen09WrV3mx7M8//5zvh82bN5NUKqWwsDC6fv06hYWFUUZGBkVGRpKrqytJJBI992kHBwfSaDTUrFkzAkD169en/fv30927dyk5OZmGDh1KzZs3p5SU" +
                    "FOrZsyedOHGCjh8/Tjdv3qQJEybQ7t276eHDh3T27Flq3rw5yeVyWrduHaWlpdH9+/dp9erVHJfLly+nzMxMOnfuHEVFRdG9e/cIAB07doymTp3K53nm" +
                    "zBkaP358tUMj8LxjQ2QyGUkkEho0aBCVlJRQaWkpXb58mebPn8/Da4mIWrZsSTk5ObR+/Xpq3LgxRUdH0+LFi2n8+PFERDR27Fhq3LgxZWZm0ubNm6lZ" +
                    "s2a0du1aevDgAfXp04cKCwspOTmZRo8eTT/88AMREf3yyy/09ttvU0pKCt24cUMvLiAyMpJKS0spMTGRkpKSKCkpidzd3SkmJoa2bNlCI0aMILVaTb16" +
                    "9aL27dtTSUkJffzxx/TTTz/R8ePHaerUqZSdnU3dunUjOzs7+u6770ilUtHAgQOpe/fudPPmTcrKyqJZs2bRwIEDqaKigrZt20ZvvPEGnTx5ktLS0qhr" +
                    "1670+PFjevLkCY0fP56mT59ORETDhg2j+fPn05MnT6h169akUqlowIABlJiYSFu3buUEAf8XF1BSUkJLly7lsRgAqF+/fnTp0iXq0qULde7cmYiIevfu" +
                    "TRs2bKD79+8TAGratCmPUj116hT985//pGbNmlFmZiZ9++231KdPHx6/MGrUKFq5ciUREXXs2JG2bt1KmzZtoqZNm9LOnTspPT2dPD09KSUlhcrLy2nq" +
                    "1Kk0btw4vSrxEomELC0tKTs7m0JCQqi4uJgGDRpEACg7O5sWLFhAFRUV9Prrr9P27dt5xXYAdOrUKYqNjSVbW1vS6XQUFhZGgwYNogMHDhARkY+PDy1d" +
                    "upQePHhAgYGBpFar6Y033qCEhATatm2bXvVxiURC9erVo7y8POrTpw8BoMrKSmrfvj0BoKSkJLp+/ToNHDiQdu7cSffv36cpU6aQUqmk4OBgatOmDeXl" +
                    "5dHs2bNp5cqVpNVqafLkyTRu3DjS6XT8YpHL5bwFBAQQEVGPHj1ILpfTuXPnKC4ujpo3b04ff/wxabVa6tatG507d45yc3NpyZIl1L9/f14VfuXKlTRw" +
                    "4EC6efMmXb58mXr27EmXLl2irl27UqdOnYiIKDg4mAYOHEhERNOnT6fhw4fTgwcPOLHIycmhr7/+ml8qSqWS5s6dW+14GfwZgWSMwjo6OtL48eNp/fr1" +
                    "VFJSQr/++iu1atWKtFot+fv708KFCykzM5POnz9PX331FTk4OFDz5s2JiMjW1pYTlv3791NUVBQdOnSIlEolNWjQgBITE2nlypUEgBo1akRarZbnspg2" +
                    "bRoVFBToBd0cPHiQbt26Re+//z5NmDCBJkyYQDY2NnT69Gn6/vvvaevWrfTo0SPas2cPRURE0N27d2nbtm20YcMGHgX62Wef8TmOGjWKlEolp9Dh4eGU" +
                    "kJBAAOhf//oXlZSU8Gfd3NyIiKhp06Z07Ngx+umnn/h3d+7coSVLltDMmTPp3r179Oqrr/LAqMLCQqPgIUYsli9fTjKZjKysrEgul5O9vT3Nnz+ffvnl" +
                    "FwoNDaWioiKaNm0aOTs7ExFRQEAALV26lG7dusVD9SMiIujnn3+mtLQ0+vnnnwkAlZaW0sSJE/k6qlQqGj9+PLVt25Z27txJkZGRdO7cOcrPzycAtH37" +
                    "djp37hwf57Fjx+jo0aP89wqFgkpLS2nAgAEUHh5OBw4coMGDB1N+fj75+vryA7V582Y6e/YsyWQykslkFB0dTeHh4dSjRw/SarVkY2PDI3h1Oh35+fnR" +
                    "/Pnz6ebNmxQYGMgjkQ1xxi6v0aNHExHRjh07+JquWbOGHB0diYg44WAX3p49eygrK4t27dpFERERlJKSQqGhobR161a6cuUKfzYyMpJOnTrFf8fWyc/P" +
                    "j4iIOnToQHK5nMrKyujs2bMUGRlJ4eHhVFJSQj169KD58+dTWloaf9/IkSOprKyM/793795ERNSkSRP69NNPKTIyknbt2kVFRUU0cuRIWrdund54Zs6c" +
                    "SU+ePCGJREJpaWm0aNEiksvlJJPJKDs7m2bNmlVrxEL+R5WbTBl08uRJrF69Gtu3b8f27dtx5MgR7Nu3D66urtBoNHBxcUFMTAwOHz6Mxo0bY8WKFfD0" +
                    "9MTy5ct5+jdW8+PAgQOIj4+Hh4cHTp8+DZVKxbXSMpkM1tbW0Ol0vCaqhYWFUaCMhYUFbt++zZWVQpmayaMVFRVYvnw5iAhBQUFIT0/H+++/j1u3bmHD" +
                    "hg1Yv349Tpw4gfj4eDg6OvLUZcyqUlxczDXRNjY2cHFxQW5uLnx8fAD87marUCj0ZFelUqkn/zJZdfLkyZg8eTL27t2LYcOG6Y1Zq9WipKRET08zffp0" +
                    "zJw5E0OHDoWlpSVGjhwJuVyOvLw8/Prrr1i7di2aNWuGf//732jXrh22bNmCkSNH4tGjR9i5cyfXnQjrYNja2kKpVKJevXqIjIzEgQMHsHbtWkyaNIln" +
                    "t7a2tkZpaameItSwBCHLM/r111/j119/xauvvopNmzbh0aNHetnVHRwc+Hy8vb1x69YtXgW9YcOG+O233+Dt7c0DooQpF4kIU6dOxccff4x9+/Zh6NCh" +
                    "XGYnIkyfPh2JiYmwtraGra0tYmNjMXLkSK7c9vPzw5UrV+Dq6org4GDI5XKUl5fjm2++gVarRefOnZGZmYmRI0eioqJCzxxsKiiLJexlegeJRIJLly5h" +
                    "69atsLOzw5UrV5CWloYRI0YgNzeXv4/tYTs7OxQXF8PX1xcFBQWYMWMG3nvvPbz11lt8fSsqKlBYWAgXFxfer6+vL8eHQqGAvb09x7+zs7PZtIvPAn+I" +
                    "WLBNVllZiZs3b2LXrl0YNmwYCgoK8NZbbyEiIgJPnjzhFoAdO3YgIyMDu3btAvB7Wji2EKGhodi0aRN++eUXzJkzBz/++COGDh0KhUKBlStXomHDhqhf" +
                    "vz5PB8dMRky55erqakQUhg0bhmPHjnGCwuqhenl54dNPP8WECRPwr3/9C3l5efjkk0/wzjvvwN7eHmq1Ghs2bEC3bt1w5swZ+Pj4IDc3FzY2NtixYwdW" +
                    "rVoFqVTK82nu3buXR8ceP34cI0eORExMDN/seXl5/FA0aNAANjY2UCqVPAJVJpMhMjISx48fx507d7BkyRLMnz8fEokEcrkclpaW+Pe//42ePXtywpif" +
                    "n88VrwEBAfjtt9+wYMECbN26FfPmzcOZM2fw8OFDREZGonPnzgB+T2n/2muvobCwEO+++y5++OEHKBQKjlcigq2tLSwtLfnG7d27Nzw8PFC/fn3MmjUL" +
                    "Op0ODRs25PNxdHQ0qvNpb28PV1dXREdH4/Hjx/D19cX69ethZ2cHuVwOW1tbREdHY+LEiYiOjubWgHfeeQd79uxBfHw8zp49i2PHjqFNmzZ4/PgxVq5c" +
                    "iYyMDDg6OsLCwgJyuRxRUVE4fPgw7t27h2XLlmHevHkgIvTt2xdBQUHc0sQuNp1OhxEjRmDVqlXYtWsXBg4ciN69e6OyshLjx4/HoUOHMGXKFGRkZGDu" +
                    "3LkYN24cLC0t0aBBAz5fBwcH2Nvbm7TgyOVy2NjYQKfTYc2aNfjoo4+Ql5eHVq1aoU+fPti0aRPs7Ozg6urK38dqzRw8eBD37t3D+PHjsXnzZiQnJ6N+" +
                    "/fp47bXX0KxZM9y9exdr1qzBhx9+iJCQEJw6dQqZmZlo06YNXF1dERISgtDQUHzyySdwc3ODr68vUlNTsWLFChw5cgTp6ek1itQ2aaUCsOiPEgyJRIL9" +
                    "+/fj+vXrCAgIgKOjI0JDQ/HZZ59Bo9GgoqIC0dHR2L59O3x9fdGlSxfs27cPixcvRn5+Ph49egQrKyvcu3cPK1asgLW1NYKCgnDx4kVMmTIFpaWlUKvV" +
                    "OHPmDO7evctv6JMnT/KEpXfu3MG5c+f4xqioqMDdu3dRUFCA7OxsZGdn49KlS0hNTeWhzDExMejatSs8PT2xaNEi7N27FxKJBFevXsX169exb98+yGQy" +
                    "lJaW4vjx4ygqKoKVlRWuXr2KtLQ0Xs6vsrISe/bsgUKhgI+PD7Zv3445c+ZAo9FApVIhLi4Ot2/f5laXuLg43L17F3fv3kV8fDzKysqQkJCA9PR0XLx4" +
                    "EY0aNcKFCxd4hvLi4mK9ueTn52PlypVITU1Fr169cPLkSc6hHTp0CA8ePMDs2bOxYcMGnDp1ChkZGbh69Sr69euHJ0+e8JyM8fHxuH//Ps6dO8ermWu1" +
                    "Whw4cAA7d+5Ep06d0LBhQ8ydOxe3bt1Ceno6L4dw9epVvtkvXbqEGzducNwrlUqcOXMG2dnZuHfvHi5evIhjx47xDOZxcXGIi4tDSkoK/P39kZSUhI8/" +
                    "/hgqlQpnz57Fxo0beYHshQsXIioqCkqlEufPn8ft27dx6dIlVFRU4OrVq3o4i4+Ph0ajQWBgIC5fvoyDBw/qXSrJycmQSCRYtmwZsrOz0aJFC8TGxmLC" +
                    "hAlITU3FsWPH0KlTJzRu3BhLlixBeHg45HI5Ll++jISEBD7fq1evIjEx0cgvorKyErGxsSguLkZMTAyysrLQu3dv5OXlYdKkSXj48CEkEgmuXbvGa+x0" +
                    "6NABXbt2xRdffIHWrVsjLCwMCxcuxPnz51FcXIyePXvi5MmTWLp0KSwtLbF161YcPXoUzZo1Q2VlJT766CP89ttvePDgAdatW4eysjJ4e3tj9+7dWLx4" +
                    "MSQSCU6fPl1rHAbVpt6iLrTqjMXUM886h9qauzBh0LO2Ll260ObNm0mpVJKXlxdP91fX1uR54Uz43ur8XRXu/8gYn7a/mNXu448/Jvqd4tT5VmvZvZlI" +
                    "YsoPQGjnFzqXMNZXWD6P+WGYekYo4wtdxA3zZZgaC3sXs8Gzv4XJWAzt4oahyGxc7DvmE8C4K1NzM7SzC4syM9s+m4swi7jQrdxUSLMwk7nQpd7T0xMe" +
                    "Hh4YO3YsMjIyOOspxIewT8NQa3YLC52VhLVrhb4QwvUU+gEIc38YzsfUd4ZjMnQZF+rHmDhhDmem9oNwT7C+DPeYYQZ4U/vB3HxNhS0Yzk9YeFqIv+Tk" +
                    "ZPzwww+wtLTknxmeC6bfYuEGwnEarqG537yw7t4i1B23ehFE+FMUnCLUPRC6cv9VGZVEqBlBN5fBrc6NVeQsRBBBhGpdRCIKRBBBBJFYiCCCCCKxEEEE" +
                    "EURiIYIIIojEQgQRRBCJhQgiiCASCxFEEEEEkViIIIII1YL/B8jkBvalfqgJAAAAAElFTkSuQmCC";

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        // ── DATA ────────────────────────────────────────────────────────────
        String kode   = pendaftar.kode_booking != null ? pendaftar.kode_booking : "MDK-" + pendaftar.pendaftaran_id;
        String nama   = pendaftar.nama_peserta  != null ? pendaftar.nama_peserta.toUpperCase() : "-";
        String nik    = pendaftar.nik_peserta   != null ? pendaftar.nik_peserta  : "-";
        String alamat = pendaftar.alamat_rumah  != null ? pendaftar.alamat_rumah : "-";
        String tgl    = pendaftar.rute != null ? pendaftar.rute.getFormattedDate() : "Jadwal Belum Rilis";
        String rute   = pendaftar.rute != null
                ? nvl(pendaftar.rute.asal) + " - " + nvl(pendaftar.rute.tujuan) : "-";
        String armada = (pendaftar.kendaraan != null && pendaftar.kendaraan.nama_armada != null)
                ? pendaftar.kendaraan.nama_armada : "-";

        // ── FONTS ────────────────────────────────────────────────────────────
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.WINANSI, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);

        // ── LOAD LOGO ────────────────────────────────────────────────────────
        Image logoDishub   = loadLogoFromBase64(LOGO_DISHUB_B64);
        Image logoSeulamat = loadLogoFromBase64(LOGO_SEULAMAT_B64);

        // ════════════════════════════════════════════════════════════════════
        //  1. HEADER: Logo kiri (Dishub) + Logo kanan (Seulamat)
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tHeader = new PdfPTable(3);
        tHeader.setWidthPercentage(100);
        tHeader.setWidths(new float[]{ 3.5f, 2f, 3.5f });
        tHeader.setSpacingAfter(0f);

        // Kiri: logo Dishub Aceh
        PdfPCell cLeft = new PdfPCell();
        cLeft.setBorder(Rectangle.NO_BORDER);
        cLeft.setBackgroundColor(BG_DARK);
        cLeft.setPaddingTop(10);
        cLeft.setPaddingBottom(10);
        cLeft.setPaddingLeft(4);
        cLeft.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoDishub != null) {
            // logo dishub landscape 1502x430 → scale to height ~36pt
            logoDishub.scaleToFit(160, 46);
            cLeft.addElement(logoDishub);
        } else {
            cLeft.addElement(new Phrase("DISHUB ACEH", new Font(bfBold, 10, Font.NORMAL, WHITE)));
        }
        tHeader.addCell(cLeft);

        // Tengah: kosong spacer
        PdfPCell cMid = new PdfPCell(new Phrase(" "));
        cMid.setBorder(Rectangle.NO_BORDER);
        cMid.setBackgroundColor(BG_DARK);
        tHeader.addCell(cMid);

        // Kanan: logo Seulamat
        PdfPCell cRight = new PdfPCell();
        cRight.setBorder(Rectangle.NO_BORDER);
        cRight.setBackgroundColor(BG_DARK);
        cRight.setPaddingTop(10);
        cRight.setPaddingBottom(10);
        cRight.setPaddingRight(4);
        cRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cRight.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoSeulamat != null) {
            // logo seulamat landscape 2000x823 → scale to height ~36pt
            logoSeulamat.scaleToFit(120, 46);
            logoSeulamat.setAlignment(Image.ALIGN_RIGHT);
            cRight.addElement(logoSeulamat);
        } else {
            Paragraph pRight = new Paragraph("Seulamat", new Font(bfBold, 10, Font.NORMAL, WHITE));
            pRight.setAlignment(Element.ALIGN_RIGHT);
            cRight.addElement(pRight);
        }
        tHeader.addCell(cRight);

        document.add(tHeader);

        // ════════════════════════════════════════════════════════════════════
        //  2. JUDUL E-TIKET
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tJudul = new PdfPTable(1);
        tJudul.setWidthPercentage(100);
        tJudul.setSpacingAfter(8f);

        Paragraph pJudul = new Paragraph();
        pJudul.setAlignment(Element.ALIGN_CENTER);
        pJudul.add(new Chunk("E-TIKET\n",            new Font(bfBold, 56, Font.NORMAL, WHITE)));
        pJudul.add(new Chunk("MUDIK GRATIS\n",        new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));
        pJudul.add(new Chunk("PEMERINTAH ACEH 2026", new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));

        PdfPCell cJudul = new PdfPCell();
        cJudul.setBorder(Rectangle.NO_BORDER);
        cJudul.setBackgroundColor(BG_DARK);
        cJudul.setHorizontalAlignment(Element.ALIGN_CENTER);
        cJudul.setPaddingTop(4);
        cJudul.setPaddingBottom(14);
        cJudul.addElement(pJudul);
        tJudul.addCell(cJudul);
        document.add(tJudul);

        // ════════════════════════════════════════════════════════════════════
        //  3. KARTU PUTIH
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tCard = new PdfPTable(1);
        tCard.setWidthPercentage(100);
        tCard.setSpacingAfter(6f);

        PdfPCell cCard = new PdfPCell();
        cCard.setBorder(Rectangle.BOX);
        cCard.setBorderColor(SLATE_200);
        cCard.setBorderWidth(1f);
        cCard.setBackgroundColor(WHITE);
        cCard.setPadding(0);

        // ── Kode booking ──────────────────────────────────────────────────
        PdfPTable tKode = new PdfPTable(1);
        tKode.setWidthPercentage(100);
        PdfPCell cKode = new PdfPCell(new Phrase(kode, new Font(bfBold, 13, Font.NORMAL, BLUE_PRIMARY)));
        cKode.setHorizontalAlignment(Element.ALIGN_CENTER);
        cKode.setBorder(Rectangle.BOTTOM);
        cKode.setBorderColor(SLATE_200);
        cKode.setBorderWidth(0.8f);
        cKode.setBackgroundColor(WHITE);
        cKode.setPaddingTop(12);
        cKode.setPaddingBottom(10);
        tKode.addCell(cKode);
        cCard.addElement(tKode);

        // ── Data peserta (kiri) + QR (kanan) ──────────────────────────────
        PdfPTable tPeserta = new PdfPTable(2);
        tPeserta.setWidthPercentage(100);
        tPeserta.setWidths(new float[]{ 3f, 2f });

        PdfPCell cKiri = new PdfPCell();
        cKiri.setBorder(Rectangle.NO_BORDER);
        cKiri.setBackgroundColor(WHITE);
        cKiri.setPaddingLeft(16);
        cKiri.setPaddingTop(14);
        cKiri.setPaddingRight(8);
        cKiri.setPaddingBottom(10);
        cKiri.addElement(makeField(bf, bfBold, "Nama Penumpang", nama));
        cKiri.addElement(makeSpacer(10));
        cKiri.addElement(makeField(bf, bfBold, "NIK", nik));
        cKiri.addElement(makeSpacer(10));
        cKiri.addElement(makeField(bf, bfBold, "Alamat Domisili", alamat));
        tPeserta.addCell(cKiri);

        // QR code
        String qrData = kode + ";" + nik + ";BUS:" + armada;
        Image qrImg = generateQRCodeImage(qrData, 200);

        PdfPCell cQrOuter = new PdfPCell();
        cQrOuter.setBorder(Rectangle.NO_BORDER);
        cQrOuter.setBackgroundColor(WHITE);
        cQrOuter.setHorizontalAlignment(Element.ALIGN_CENTER);
        cQrOuter.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cQrOuter.setPaddingTop(14);
        cQrOuter.setPaddingRight(16);
        cQrOuter.setPaddingBottom(10);
        cQrOuter.setPaddingLeft(4);

        PdfPTable tQr = new PdfPTable(1);
        tQr.setWidthPercentage(100);
        PdfPCell cQrInner = new PdfPCell(qrImg, true);
        cQrInner.setBorder(Rectangle.BOX);
        cQrInner.setBorderColor(BLUE_PRIMARY);
        cQrInner.setBorderWidth(4f);
        cQrInner.setPadding(4);
        cQrInner.setBackgroundColor(WHITE);
        cQrInner.setHorizontalAlignment(Element.ALIGN_CENTER);
        tQr.addCell(cQrInner);
        cQrOuter.addElement(tQr);
        tPeserta.addCell(cQrOuter);

        cCard.addElement(tPeserta);

        // ── Strip biru keberangkatan ───────────────────────────────────────
        PdfPTable tStrip = new PdfPTable(1);
        tStrip.setWidthPercentage(100);
        PdfPCell cStrip = new PdfPCell(
                new Phrase("Keberangkatan : " + tgl, new Font(bfBold, 11, Font.NORMAL, WHITE)));
        cStrip.setHorizontalAlignment(Element.ALIGN_CENTER);
        cStrip.setBackgroundColor(BLUE_PRIMARY);
        cStrip.setBorder(Rectangle.NO_BORDER);
        cStrip.setPaddingTop(10);
        cStrip.setPaddingBottom(10);
        tStrip.addCell(cStrip);
        cCard.addElement(tStrip);

        // ── Rute & Armada ──────────────────────────────────────────────────
        PdfPTable tRute = new PdfPTable(2);
        tRute.setWidthPercentage(100);
        tRute.setWidths(new float[]{ 1f, 1f });

        PdfPCell cRute = new PdfPCell();
        cRute.setBorder(Rectangle.NO_BORDER);
        cRute.setBackgroundColor(WHITE);
        cRute.setPaddingLeft(16);
        cRute.setPaddingTop(14);
        cRute.setPaddingBottom(14);
        cRute.addElement(makeField(bf, bfBold, "Rute Perjalanan", rute));
        tRute.addCell(cRute);

        PdfPCell cArmada = new PdfPCell();
        cArmada.setBorder(Rectangle.NO_BORDER);
        cArmada.setBackgroundColor(WHITE);
        cArmada.setPaddingLeft(10);
        cArmada.setPaddingTop(14);
        cArmada.setPaddingBottom(14);
        cArmada.addElement(makeField(bf, bfBold, "Armada Bus", armada));
        tRute.addCell(cArmada);
        cCard.addElement(tRute);

        // ── Separator ─────────────────────────────────────────────────────
        PdfPTable tSep = new PdfPTable(1);
        tSep.setWidthPercentage(100);
        PdfPCell cSep = new PdfPCell(new Phrase(" "));
        cSep.setBorder(Rectangle.TOP);
        cSep.setBorderColor(SLATE_200);
        cSep.setBorderWidth(1f);
        cSep.setBackgroundColor(WHITE);
        cSep.setPaddingTop(2);
        cSep.setPaddingBottom(2);
        tSep.addCell(cSep);
        cCard.addElement(tSep);

        // ── Catatan & pengaduan ────────────────────────────────────────────
        Paragraph pNote = new Paragraph();
        pNote.setAlignment(Element.ALIGN_CENTER);
        pNote.setLeading(17f);
        pNote.add(new Chunk("*Harap datang 1 jam sebelum keberangkatan\n",
                new Font(bf, 9, Font.ITALIC, SLATE_500)));
        pNote.add(new Chunk("*Tunjukkan QR Code ini kepada petugas\n\n",
                new Font(bf, 9, Font.ITALIC, SLATE_500)));
        pNote.add(new Chunk("Layanan Pengaduan (WA) :\n",
                new Font(bf, 9, Font.NORMAL, SLATE_500)));
        pNote.add(new Chunk("08217653093 / 08217653095",
                new Font(bfBold, 12, Font.NORMAL, BLUE_PRIMARY)));

        PdfPCell cNote = new PdfPCell();
        cNote.setBorder(Rectangle.NO_BORDER);
        cNote.setBackgroundColor(WHITE);
        cNote.setHorizontalAlignment(Element.ALIGN_CENTER);
        cNote.setPaddingTop(14);
        cNote.setPaddingBottom(18);
        cNote.addElement(pNote);

        PdfPTable tNote = new PdfPTable(1);
        tNote.setWidthPercentage(100);
        tNote.addCell(cNote);
        cCard.addElement(tNote);

        tCard.addCell(cCard);
        document.add(tCard);

        // ════════════════════════════════════════════════════════════════════
        //  4. FOOTER
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tFooter = new PdfPTable(1);
        tFooter.setWidthPercentage(100);
        tFooter.setSpacingBefore(8f);
        PdfPCell cFooter = new PdfPCell(
                new Phrase("Dinas Perhubungan Aceh  \u2022  Pemerintah Aceh 2026",
                        new Font(bf, 9, Font.NORMAL, BLUE_LIGHT_TEXT)));
        cFooter.setHorizontalAlignment(Element.ALIGN_CENTER);
        cFooter.setBorder(Rectangle.NO_BORDER);
        cFooter.setBackgroundColor(BG_DARK);
        cFooter.setPaddingTop(10);
        cFooter.setPaddingBottom(10);
        tFooter.addCell(cFooter);
        document.add(tFooter);

        document.close();
        return out.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Load logo dari classpath resources.
     * Taruh file PNG di src/main/resources/ dengan nama yang sama.
     * Return null jika file tidak ditemukan (fallback ke teks).
     */
    private Image loadLogoFromBase64(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private Paragraph makeField(BaseFont bf, BaseFont bfBold, String label, String value) {
        Paragraph p = new Paragraph();
        p.setLeading(15f);
        p.add(new Chunk(label + "\n",                new Font(bf,     9,  Font.NORMAL, BLUE_LABEL)));
        p.add(new Chunk(value != null ? value : "-", new Font(bfBold, 13, Font.NORMAL, SLATE_900)));
        return p;
    }

    private Paragraph makeSpacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private Image generateQRCodeImage(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return Image.getInstance(png.toByteArray());
    }

    private String nvl(String s) {
        return s != null ? s : "-";
    }
}
