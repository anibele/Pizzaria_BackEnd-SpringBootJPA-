package inf.anibele.pizzariamauabackend.config;

import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import inf.anibele.pizzariamauabackend.repository.ProdutoRepository;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {

    private static final String BASE_URL =
            "https://raw.githubusercontent.com/anibele/Pizzaria_FrontEnd-React-Vite-Axios/refs/heads/main/imagens_cardapio/";

    @Bean
    public CommandLineRunner carregarUsuarios(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (repository.findByUsername("gerente1").isEmpty()) {
                Usuario gerente = new Usuario();
                gerente.setUsername("gerente1");
                gerente.setSenha(passwordEncoder.encode("gerente1"));
                gerente.setRole(RoleName.GERENTE);
                repository.save(gerente);
            }

            if (repository.findByUsername("cozinha1").isEmpty()) {
                Usuario cozinha = new Usuario();
                cozinha.setUsername("cozinha1");
                cozinha.setSenha(passwordEncoder.encode("cozinha1"));
                cozinha.setRole(RoleName.COZINHA);
                repository.save(cozinha);
            }

            for (int i = 1; i <= 10; i++) {
                String numero = String.format("%02d", i);
                String username = "mesa" + numero;

                if (repository.findByUsername(username).isEmpty()) {
                    Usuario mesa = new Usuario();
                    mesa.setUsername(username);
                    mesa.setSenha(passwordEncoder.encode(username));
                    mesa.setRole(RoleName.MESA);
                    repository.save(mesa);
                }
            }
        };
    }

    @Bean
    public CommandLineRunner carregarMesas(MesaRepository mesaRepository) {
        return args -> {
            List<Mesa> mesas = new ArrayList<>();

            for (int i = 1; i < 11; i++) {
                if (mesaRepository.findByNumero(i).isEmpty()) {
                    Mesa mesa = new Mesa();
                    mesa.setNumero(i);
                    mesa.setStatus(StatusMesa.LIVRE);
                    mesa.setAtivo(true);
                    mesas.add(mesa);
                }
            }
            mesaRepository.saveAll(mesas);
        };
    }

    @Bean
    public CommandLineRunner carregarProdutos(ProdutoRepository produtoRepository) {
        return args -> {
            List<Produto> produtos = new ArrayList<>();

            // PIZZAS SALGADAS
            produtos.add(criarProduto("Pizza Calabresa", Categoria.PIZZAS, "44.99", "pizza1.png", false, 50, "20 min",
                    "Clássica, bem temperada e com sabor marcante.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "calabresa fatiada", "cebola", "orégano")));

            produtos.add(criarProduto("Pizza 4 Queijos", Categoria.PIZZAS, "49.99", "pizza2.png", false, 50, "20 min",
                    "Cremosa e intensa, com sabor equilibrado de queijos.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "provolone", "parmesão", "gorgonzola")));

            produtos.add(criarProduto("Pizza Portuguesa", Categoria.PIZZAS, "52.99", "pizza3.png", false, 50, "22 min",
                    "Tradicional e completa, com ingredientes muito apreciados.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "presunto", "ovo", "cebola", "azeitona", "orégano")));

            produtos.add(criarProduto("Pizza Frango com Catupiry", Categoria.PIZZAS, "54.99", "pizza4.png", false, 50, "22 min",
                    "Suave, cremosa e muito popular entre os clientes.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "frango desfiado", "catupiry", "milho")));

            produtos.add(criarProduto("Pizza Bacon", Categoria.PIZZAS, "52.99", "pizza5.png", false, 50, "20 min",
                    "Crocante e saborosa, com bacon bem dourado.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "bacon crocante", "orégano")));

            produtos.add(criarProduto("Pizza Pepperoni", Categoria.PIZZAS, "56.99", "pizza6.png", false, 50, "18 min",
                    "Sabor intenso, levemente picante e muito pedido.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "pepperoni", "orégano")));

            produtos.add(criarProduto("Pizza Costela Desfiada", Categoria.PIZZAS, "64.99", "pizza7.png", false, 50, "25 min",
                    "Robusta, suculenta e com sabor marcante de carne.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "costela bovina desfiada", "cebola roxa", "orégano")));

            produtos.add(criarProduto("Pizza Filé Mignon com Gorgonzola", Categoria.PIZZAS, "69.99", "pizza8.png", false, 50, "25 min",
                    "Gourmet, cremosa e com combinação sofisticada.",
                    List.of("massa artesanal", "molho de tomate", "muçarela", "filé mignon", "gorgonzola", "cebola caramelizada")));

            produtos.add(criarProduto("Pizza Vegetais Grelhados", Categoria.PIZZAS, "54.99", "pizza9.png", true, 50, "18 min",
                    "Leve, colorida e preparada com vegetais grelhados.",
                    List.of("massa artesanal", "molho de tomate", "queijo vegano", "abobrinha grelhada", "berinjela grelhada", "pimentão vermelho", "tomate-cereja", "manjericão")));

            produtos.add(criarProduto("Pizza Cogumelos Trufados", Categoria.PIZZAS, "59.99", "pizza10.png", true, 50, "20 min",
                    "Gourmet e aromática, com toque refinado de trufa.",
                    List.of("massa artesanal", "molho de tomate", "queijo vegano", "shiitake", "shimeji", "champignon", "azeite trufado", "cebolinha")));

            // PIZZAS DOCES
            produtos.add(criarProduto("Pizza Chocolate ao Leite", Categoria.PIZZAS, "39.99", "pizza11.png", false, 50, "15 min",
                    "Clássica e indulgente, perfeita para quem ama chocolate.",
                    List.of("massa artesanal doce", "chocolate ao leite", "raspas de chocolate", "açúcar")));

            produtos.add(criarProduto("Pizza Chocolate com Morango", Categoria.PIZZAS, "44.99", "pizza12.png", false, 50, "15 min",
                    "Doce e equilibrada, com contraste entre chocolate e fruta.",
                    List.of("massa artesanal doce", "chocolate ao leite", "morangos frescos", "raspas de chocolate")));

            produtos.add(criarProduto("Pizza Banana com Canela", Categoria.PIZZAS, "38.99", "pizza13.png", false, 50, "15 min",
                    "Tradicional, aconchegante e com aroma irresistível.",
                    List.of("massa artesanal doce", "banana fatiada", "canela", "açúcar mascavo", "leite condensado")));

            produtos.add(criarProduto("Pizza Prestígio", Categoria.PIZZAS, "42.99", "pizza14.png", false, 50, "15 min",
                    "Chocolate com coco em uma combinação clássica e querida.",
                    List.of("massa artesanal doce", "chocolate ao leite", "coco ralado", "leite condensado")));

            produtos.add(criarProduto("Pizza Maçã com Canela", Categoria.PIZZAS, "39.99", "pizza15.png", true, 50, "15 min",
                    "Leve, aromática e com perfil mais artesanal.",
                    List.of("massa artesanal doce", "maçã caramelizada", "canela", "açúcar mascavo", "amêndoas laminadas")));

            // SOBREMESAS
            produtos.add(criarProduto("Brownie com Sorvete", Categoria.SOBREMESAS, "24.99", "sobremesa1.png", false, 50, "10 min",
                    "Quente e gelado na medida certa, com bastante chocolate.",
                    List.of("brownie de chocolate", "sorvete de creme", "calda de chocolate", "raspas de chocolate")));

            produtos.add(criarProduto("Taça de Morangos com Chocolate", Categoria.SOBREMESAS, "26.99", "sobremesa2.png", false, 50, "10 min",
                    "Elegante e refrescante, com bom contraste de sabores.",
                    List.of("morangos frescos", "ganache de chocolate", "raspas de chocolate", "chantilly")));

            produtos.add(criarProduto("Cheesecake de Frutas Vermelhas", Categoria.SOBREMESAS, "28.99", "sobremesa3.png", false, 50, "12 min",
                    "Cremoso, delicado e com toque levemente ácido.",
                    List.of("creme de cheesecake", "base de biscoito", "morangos", "framboesas", "amoras", "calda de frutas vermelhas")));

            produtos.add(criarProduto("Petit Gâteau", Categoria.SOBREMESAS, "27.99", "sobremesa4.png", false, 50, "12 min",
                    "Clássico francês com centro derretido e sorvete.",
                    List.of("bolo de chocolate", "recheio cremoso", "sorvete de creme", "calda de chocolate")));

            produtos.add(criarProduto("Mousse de Chocolate Vegano", Categoria.SOBREMESAS, "22.99", "sobremesa5.png", true, 50, "10 min",
                    "Cremosa, intensa e feita com ingredientes vegetais.",
                    List.of("chocolate amargo vegano", "creme de coco", "cacau em pó", "raspas de chocolate")));

            // ACOMPANHAMENTOS
            produtos.add(criarProduto("Batata Frita com Cheddar e Bacon", Categoria.ACOMPANHAMENTOS, "29.99", "acompanhamento1.png", false, 50, "12 min",
                    "Crocante, cremosa e muito pedida no cardápio.",
                    List.of("batata frita", "cheddar cremoso", "bacon crocante")));

            produtos.add(criarProduto("Pão de Alho Gratinado", Categoria.ACOMPANHAMENTOS, "21.99", "acompanhamento2.png", false, 50, "10 min",
                    "Clássico de pizzaria, com sabor de alho e queijo.",
                    List.of("pão artesanal", "manteiga de alho", "muçarela gratinada", "salsinha")));

            produtos.add(criarProduto("Palitos de Muçarela", Categoria.ACOMPANHAMENTOS, "24.99", "acompanhamento3.png", false, 50, "10 min",
                    "Crocantes por fora e com queijo derretido por dentro.",
                    List.of("muçarela empanada", "farinha panko", "molho marinara")));

            produtos.add(criarProduto("Frango Crocante", Categoria.ACOMPANHAMENTOS, "27.99", "acompanhamento4.png", false, 50, "12 min",
                    "Petisco sequinho, crocante e muito saboroso.",
                    List.of("frango empanado", "temperos da casa", "molho de acompanhamento")));

            produtos.add(criarProduto("Batata Rústica com Ervas", Categoria.ACOMPANHAMENTOS, "20.99", "acompanhamento5.png", true, 50, "15 min",
                    "Assada, aromática e com um toque mais leve.",
                    List.of("batata rústica", "alecrim", "tomilho", "azeite extravirgem", "flor de sal")));

            // BEBIDAS
            produtos.add(criarProduto("Água Mineral com Gás", Categoria.BEBIDAS, "8.99", "bebida1.png", true, 50, "2 min",
                    "Refrescante, leve e ideal para acompanhar as refeições.",
                    List.of("água mineral com gás", "gelo cristalino")));

            produtos.add(criarProduto("Coca-Cola com Gelo e Limão", Categoria.BEBIDAS, "9.99", "bebida2.png", true, 50, "2 min",
                    "Clássica, gelada e com toque cítrico refrescante.",
                    List.of("refrigerante cola", "gelo", "limão-siciliano")));

            produtos.add(criarProduto("Guaraná Antárctica com Gelo", Categoria.BEBIDAS, "9.99", "bebida3.png", true, 50, "2 min",
                    "Tradicional, doce e muito popular no Brasil.",
                    List.of("refrigerante de guaraná", "gelo cristalino")));

            produtos.add(criarProduto("Suco de Laranja Natural", Categoria.BEBIDAS, "12.99", "bebida4.png", true, 50, "5 min",
                    "Fresco, cítrico e feito com fruta natural.",
                    List.of("laranja natural", "gelo", "sem açúcar")));

            produtos.add(criarProduto("Suco de Morango Natural", Categoria.BEBIDAS, "13.99", "bebida5.png", true, 50, "5 min",
                    "Doce, vibrante e com sabor de fruta fresca.",
                    List.of("morangos frescos", "água gelada", "gelo")));

            produtos.add(criarProduto("Suco de Abacaxi com Hortelã", Categoria.BEBIDAS, "13.99", "bebida6.png", true, 50, "5 min",
                    "Tropical, refrescante e muito aromático.",
                    List.of("abacaxi", "hortelã fresca", "água gelada", "gelo")));

            produtos.add(criarProduto("Chopp Pilsen", Categoria.BEBIDAS, "14.99", "bebida7.png", false, 50, "3 min",
                    "Bem gelado, com espuma cremosa e visual clássico.",
                    List.of("chopp pilsen", "espuma cremosa", "carbonatação natural")));

            produtos.add(criarProduto("Gin Tônica", Categoria.BEBIDAS, "28.99", "bebida8.png", false, 50, "5 min",
                    "Elegante, aromático e com aparência premium.",
                    List.of("gin", "água tônica", "limão", "zimbro", "gelo")));

            produtos.add(criarProduto("Vinho Tinto", Categoria.BEBIDAS, "32.99", "bebida9.png", false, 50, "2 min",
                    "Encorpado, sofisticado e ideal para harmonização.",
                    List.of("vinho tinto")));

            List<Produto> novos = produtos.stream()
                    .filter(produto -> produtoRepository.findByNome(produto.getNome()).isEmpty())
                    .toList();

            if (!novos.isEmpty()) {
                produtoRepository.saveAll(novos);
            }
        };
    }

    private Produto criarProduto(
            String nome,
            Categoria categoria,
            String precoBase,
            String imagemNome,
            boolean vegano,
            Integer qtdEstoque,
            String tempoMedioPreparo,
            String breveDescricao,
            List<String> ingredientes
    ) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setCategoria(categoria);
        produto.setPrecoBase(new BigDecimal(precoBase));
        produto.setImagemUrl(BASE_URL + imagemNome);
        produto.setAtivo(true);
        produto.setQtdEstoque(qtdEstoque);

        // Instanciação e mapeamento do novo Atributo Composto Embutido (@Embedded)
        DetalhesDescricao detalhes = new DetalhesDescricao();
        detalhes.setBreveDescricao(breveDescricao);
        detalhes.setTempoMedioPreparo(tempoMedioPreparo);
        detalhes.setPratoVegano(vegano);
        detalhes.setIngredientes(ingredientes);

        produto.setDescricao(detalhes);

        return produto;
    }
}