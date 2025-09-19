import Combine
import SwiftUI
import UIKit


class LoadingCell: UITableViewCell {
    private let activityIndicator = UIActivityIndicatorView(style: .medium)
    private let loadingLabel = UILabel()
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        backgroundColor = .clear
        
        // 역순 표시를 위한 transform
        contentView.transform = CGAffineTransform(scaleX: 1, y: -1)
        
        let stackView = UIStackView(arrangedSubviews: [activityIndicator, loadingLabel])
        stackView.axis = .horizontal
        stackView.spacing = 8
        stackView.alignment = .center
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        loadingLabel.text = "이전 메시지 로딩 중..."
        loadingLabel.font = UIFont.systemFont(ofSize: 12)
        loadingLabel.textColor = .gray
        
        contentView.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            stackView.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            stackView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 16),
            stackView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -16)
        ])
    }
    
    func configure(isLoading: Bool) {
        if isLoading {
            activityIndicator.startAnimating()
        } else {
            activityIndicator.stopAnimating()
        }
        isHidden = !isLoading
    }
}
