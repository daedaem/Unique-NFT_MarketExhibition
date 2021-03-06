package com.ssafy.unique.api.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.unique.api.config.IPFSConfig;
import com.ssafy.unique.api.request.NftReq;
import com.ssafy.unique.api.request.NftUpdateReq;
import com.ssafy.unique.api.response.NftRes;
import com.ssafy.unique.api.response.ResultRes;
import com.ssafy.unique.db.entity.FileList;
import com.ssafy.unique.db.entity.Nft;
import com.ssafy.unique.db.repository.FileListRepository;
import com.ssafy.unique.db.repository.MemberRepository;
import com.ssafy.unique.db.repository.NftRepository;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.unique.api.config.IPFSConfig;
import com.ssafy.unique.api.request.NftReq;
import com.ssafy.unique.api.request.NftUpdateReq;
import com.ssafy.unique.api.response.NftRes;
import com.ssafy.unique.api.response.ResultRes;
import com.ssafy.unique.db.entity.FileList;
import com.ssafy.unique.db.entity.Nft;
import com.ssafy.unique.db.repository.FileListRepository;
import com.ssafy.unique.db.repository.MemberRepository;
import com.ssafy.unique.db.repository.NftRepository;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;

@Service
public class IPFSServiceImpl implements IPFSService {

	private final IPFSConfig ipfsConfig;
	private final NftRepository nftRepository;
	private final MemberRepository memberRepository;
	private final FileListRepository fileRepository;

	public IPFSServiceImpl(IPFSConfig _ipfsConfig, NftRepository _nftRepository, MemberRepository _memberRepository,
						   FileListRepository _fileRepository) {
		this.ipfsConfig = _ipfsConfig;
		this.nftRepository = _nftRepository;
		this.memberRepository = _memberRepository;
		this.fileRepository = _fileRepository;
	}

	private static final int SUCCESS = 1;
	private static final int FAIL = -1;

	String uploadFolder = "upload";
	//String uploadPath = "C:" + File.separator + "SSAFY" + File.separator + "NFT";
	String uploadPath = "/usr" + File.separator + "share" + File.separator + "nginx" + File.separator + "html";


	@Override
	public NftRes saveFile(NftReq nftReq, MultipartHttpServletRequest request) {
		NftRes nftRes = new NftRes();

		try {
			MultipartFile file = request.getFile("file");

			if (file != null) {
				// ??????: ????????? ???????????? ??????

				// ?????? ?????? ?????? ??????
				File uploadDir = new File(uploadPath + File.separator + uploadFolder);
				if (!uploadDir.exists())
					uploadDir.mkdirs();

				// ?????? ???????????? ??????
				String fileName = file.getOriginalFilename();

				// Random File Id + File Extension ?????? ????????? ???????????? ??????
				UUID uuid = UUID.randomUUID();
				String extension = FilenameUtils.getExtension(fileName);
				String savingFileName = uuid + "." + extension;

				// ?????? ??????
				File destFile = new File(uploadPath + File.separator + uploadFolder + File.separator + savingFileName);
				System.out.println(uploadPath + File.separator + uploadFolder + File.separator + savingFileName);

				// ?????? url
				String fileUrl = uploadFolder + File.separator + savingFileName;





				// IPFS Upload
				// file.getBytes() ????????? ????????? ????????? ?????? ????????????, ????????? ?????? ????????? ????????? ????????????
				InputStream stream = new ByteArrayInputStream(file.getBytes());
				NamedStreamable.InputStreamWrapper inputStreamWrapper = new NamedStreamable.InputStreamWrapper(stream);
				IPFS ipfs = ipfsConfig.ipfs;

				MerkleNode merkleNode = ipfs.add(inputStreamWrapper).get(0);

				// MerkleNode?????? nftWorkUri??? ?????????
				nftReq.setNftWorkUri(merkleNode.hash.toBase58());

				// nftWorkUri??? ?????? ??????????????? DB?????? ????????????, ????????? ????????? ??????


				// Security Context?????? nftCreatorSeq??? ?????????
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				nftReq.setNftAuthorSeq(Long.parseLong(authentication.getName()));
				// Content-type
				nftReq.setNftType(file.getContentType());
				// ?????????????????? ??????
				ObjectMapper mapper = new ObjectMapper();
				String jsonStr = mapper.writeValueAsString(nftReq);
				System.out.println(jsonStr);

				stream = new ByteArrayInputStream(jsonStr.getBytes());
				inputStreamWrapper = new NamedStreamable.InputStreamWrapper(stream);
				merkleNode = ipfs.add(inputStreamWrapper).get(0);

				// Owner Address??? ?????????
				String ownerAddress = memberRepository.findMemberAddressByMemberSeq(nftReq.getNftAuthorSeq());
				System.out.println(ownerAddress);
				// MetadataUri??? ?????????
				String nftMetadataUri = merkleNode.hash.toBase58();

				// DB??? ??????
				Nft nft = nftRepository.save(Nft.builder()
						.nftAuthorSeq(nftReq.getNftAuthorSeq())
						.nftAuthorName(nftReq.getNftAuthorName())
						.nftOwnerSeq(nftReq.getNftAuthorSeq())
						.nftOwnerAddress(ownerAddress)
						.nftWorkUri(nftReq.getNftWorkUri())
						.nftMetadataUri(nftMetadataUri)
						.nftName(nftReq.getNftName())
						.nftType(nftReq.getNftType())
						.nftDescription(nftReq.getNftDescription())
						.fileUrl(fileUrl)
						.build()
				);



				// ?????? ?????? => ?????? ????????? ?????? ???????????? getBytes()?????? ?????? ??????
				file.transferTo(destFile);
				// FILE_LIST DB??? ?????? ?????? => NFT_SEQ??? ?????? ???????????? ?????? ???????????????
				fileRepository.save(FileList.builder()
						.nftSeq(nft.getNftSeq())
						.fileName(fileName)
						.fileSize(file.getSize())
						.fileContentType(file.getContentType())
						.fileUrl(fileUrl)
						.build());

				nftRes.setResult(SUCCESS);
				nftRes.setNftSeq(nft.getNftSeq());
				nftRes.setNftMetadataUri(nftMetadataUri);
			} else {
				System.out.println("null file");
				nftRes.setResult(FAIL);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error whilst communicating whit the IPFS node", e);
		} finally {
			return nftRes;
		}
	}

	@Override
	public byte[] LoadFile(String hash) {

		try {
			IPFS ipfs = ipfsConfig.ipfs;

			Multihash filePointer = Multihash.fromBase58(hash);
			return ipfs.cat(filePointer);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error whilst communicating whit the IPFS node", e);
		}

	}

	@SuppressWarnings("finally")
	@Override
	public ResultRes updateNFT(NftUpdateReq nftUpdateReq) {
		// DB??? ???????????? ?????? NFT??? ?????? ????????????

		ResultRes resultRes = new ResultRes();

		try {
			// Security Context?????? nftCreatorSeq??? ?????????
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			Long nftSeq = Long.parseLong(authentication.getName());

			// Owner Address??? ?????????
			String ownerAddress = memberRepository.findMemberAddressByMemberSeq(nftSeq);

			int result = nftRepository.updateNftByNftTokenIdAndNftOwnerAddress(
					nftUpdateReq.getTokenId(),
					nftUpdateReq.getContractAddress(),
					ownerAddress,
					nftUpdateReq.getMetadataUri());

			if (result == SUCCESS) {
				resultRes.setResult(SUCCESS);
			} else {
				resultRes.setResult(FAIL);
			}

		} catch (Exception e) {
			e.printStackTrace();
			resultRes.setResult(FAIL);
		} finally {
			return resultRes;
		}
	}

}
